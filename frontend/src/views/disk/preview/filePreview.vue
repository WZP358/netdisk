<template>
  <div class="file-preview-container">
    <div class="preview-header">
      <div class="file-info">
        <span class="file-name">{{ fileName }}</span>
        <span class="file-size">{{ formatSize(fileSize) }}</span>
      </div>
      <div class="preview-actions">
        <el-button icon="el-icon-download" size="small" @click="handleDownload">下载</el-button>
        <el-button icon="el-icon-close" size="small" @click="handleClose">关闭</el-button>
      </div>
    </div>

    <div class="preview-content">
      <!-- 图片预览 -->
      <div v-if="fileType === 'image'" class="image-preview">
        <el-image
          :src="fileUrl"
          fit="contain"
          :preview-src-list="[fileUrl]"
          style="width: 100%; height: 100%"
          @load="handleImageLoad"
          @error="handleImageError"
        >
          <div slot="placeholder" class="image-slot">
            <i class="el-icon-loading"></i>
            <p>加载中...</p>
          </div>
          <div slot="error" class="image-slot">
            <i class="el-icon-picture-outline"></i>
            <p>图片加载失败</p>
            <p style="font-size: 12px; color: #f56c6c; margin-top: 10px;">{{ fileUrl }}</p>
            <el-button size="small" style="margin-top: 10px" @click="handleDownload">下载文件</el-button>
          </div>
        </el-image>
      </div>

      <!-- 视频预览 -->
      <div v-else-if="fileType === 'video'" class="video-preview">
        <video :src="fileUrl" controls style="width: 100%; max-height: 100%">
          您的浏览器不支持视频播放
        </video>
      </div>

      <!-- 音频预览 -->
      <div v-else-if="fileType === 'audio'" class="audio-preview">
        <div class="audio-container">
          <div class="audio-cover">
            <i class="el-icon-headset"></i>
          </div>
          <div class="audio-info">
            <h3>{{ fileName }}</h3>
            <audio ref="audioPlayer" :src="fileUrl" controls style="width: 100%; margin-top: 20px">
              您的浏览器不支持音频播放
            </audio>
          </div>
        </div>
      </div>

      <!-- PDF预览 -->
      <div v-else-if="fileType === 'pdf'" class="pdf-preview">
        <iframe :src="fileUrl" style="width: 100%; height: 100%; border: none"></iframe>
      </div>

      <!-- Office文档预览 (Word, Excel, PPT) -->
      <div v-else-if="fileType === 'office'" class="office-preview">
        <iframe
          :src="getOfficePreviewUrl()"
          style="width: 100%; height: 100%; border: none"
        ></iframe>
      </div>

      <!-- 文本文件预览 -->
      <div v-else-if="fileType === 'text'" class="text-preview">
        <pre>{{ textContent }}</pre>
      </div>

      <!-- 不支持的文件类型 -->
      <div v-else class="unsupported-preview">
        <div class="unsupported-content">
          <i class="el-icon-document"></i>
          <p>暂不支持该文件类型的在线预览</p>
          <el-button type="primary" @click="handleDownload">下载文件</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import axios from 'axios';

export default {
  name: 'FilePreview',
  data() {
    return {
      fileName: '',
      fileUrl: '',
      fileSize: 0,
      fileType: '',
      textContent: '',
      baseUrl: process.env.VUE_APP_BASE_API,
    };
  },
  created() {
    console.log('filePreview created, route params:', this.$route.params);
    
    const { url, name, size, type } = this.$route.params;
    
    if (!url || !name) {
      console.error('文件信息不完整:', { url, name, size, type });
      this.$message.error('文件信息不完整，无法预览');
      return;
    }
    
    this.fileName = name || '未知文件';
    this.fileSize = size || 0;
    
    // 构建完整的文件URL
    if (url) {
      if (url.startsWith('http://') || url.startsWith('https://')) {
        this.fileUrl = url;
      } else {
        // 暂时不编码，让后端处理
        this.fileUrl = this.baseUrl + url;
        console.log('原始URL（未编码）:', this.fileUrl);
      }
    }

    console.log('文件URL:', this.fileUrl);
    console.log('文件名:', this.fileName);
    console.log('文件大小:', this.fileSize);
    console.log('文件类型:', type);

    // 根据文件扩展名判断文件类型
    this.fileType = this.getFileType(name, type);
    console.log('判断的文件类型:', this.fileType);

    // 如果是文本文件，加载内容
    if (this.fileType === 'text') {
      this.loadTextContent();
    }
  },
  methods: {
    /**
     * 根据文件名和类型判断文件类型
     */
    getFileType(fileName, type) {
      if (!fileName) return 'unknown';

      const ext = fileName.split('.').pop().toLowerCase();

      // 图片类型
      const imageExts = ['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp', 'svg'];
      if (imageExts.includes(ext) || type === 0) {
        return 'image';
      }

      // 视频类型
      const videoExts = ['mp4', 'avi', 'mkv', 'mov', 'wmv', 'flv', 'webm'];
      if (videoExts.includes(ext) || type === 1) {
        return 'video';
      }

      // 音频类型
      const audioExts = ['mp3', 'wav', 'ogg', 'aac', 'flac', 'm4a', 'wma'];
      if (audioExts.includes(ext) || type === 3) {
        return 'audio';
      }

      // PDF
      if (ext === 'pdf') {
        return 'pdf';
      }

      // Office文档
      const officeExts = ['doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx'];
      if (officeExts.includes(ext) || type === 2) {
        return 'office';
      }

      // 文本文件
      const textExts = ['txt', 'md', 'json', 'xml', 'csv', 'log'];
      if (textExts.includes(ext)) {
        return 'text';
      }

      return 'unknown';
    },

    /**
     * 获取Office文档预览URL (使用微软Office Online)
     */
    getOfficePreviewUrl() {
      // 使用微软的Office Online Viewer
      const encodedUrl = encodeURIComponent(this.fileUrl);
      return `https://view.officeapps.live.com/op/embed.aspx?src=${encodedUrl}`;
    },

    /**
     * 加载文本文件内容
     */
    async loadTextContent() {
      try {
        const response = await axios.get(this.fileUrl, {
          responseType: 'text'
        });
        this.textContent = response.data;
      } catch (error) {
        this.textContent = '文件内容加载失败';
        console.error('加载文本内容失败:', error);
      }
    },

    /**
     * 格式化文件大小
     */
    formatSize(bytes) {
      if (!bytes || bytes === 0) return '0 B';
      const k = 1024;
      const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
      const i = Math.floor(Math.log(bytes) / Math.log(k));
      return (bytes / Math.pow(k, i)).toFixed(2) + ' ' + sizes[i];
    },

    /**
     * 下载文件
     */
    handleDownload() {
      const link = document.createElement('a');
      link.href = this.fileUrl;
      link.download = this.fileName;
      link.target = '_blank';
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
    },

    /**
     * 关闭预览
     */
    handleClose() {
      this.$router.go(-1);
    },

    /**
     * 图片加载成功
     */
    handleImageLoad() {
      console.log('图片加载成功:', this.fileUrl);
    },

    /**
     * 图片加载失败
     */
    handleImageError(error) {
      console.error('图片加载失败:', this.fileUrl, error);
      this.$message.error('图片加载失败，请检查文件是否存在或尝试下载');
    }
  }
};
</script>

<style lang="scss" scoped>
.file-preview-container {
  width: 100%;
  height: 100vh;
  display: flex;
  flex-direction: column;
  background-color: #f5f7fa;

  .preview-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 16px 24px;
    background-color: #fff;
    box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
    z-index: 10;

    .file-info {
      display: flex;
      align-items: center;
      gap: 16px;

      .file-name {
        font-size: 16px;
        font-weight: 500;
        color: #303133;
      }

      .file-size {
        font-size: 14px;
        color: #909399;
      }
    }

    .preview-actions {
      display: flex;
      gap: 8px;
    }
  }

  .preview-content {
    flex: 1;
    overflow: auto;
    padding: 20px;
    display: flex;
    justify-content: center;
    align-items: center;

    .image-preview {
      width: 100%;
      height: 100%;
      display: flex;
      justify-content: center;
      align-items: center;
      background-color: #f5f7fa;
      border-radius: 8px;

      ::v-deep .el-image {
        max-width: 100%;
        max-height: 100%;
        background-color: transparent;
      }

      .image-slot {
        display: flex;
        flex-direction: column;
        justify-content: center;
        align-items: center;
        color: #909399;
        font-size: 48px;
        background-color: #fff;
        padding: 40px;
        border-radius: 8px;

        p {
          margin-top: 16px;
          font-size: 14px;
        }
      }
    }

    .video-preview {
      width: 100%;
      max-width: 1200px;
      height: auto;
      background-color: #000;
      border-radius: 8px;
      overflow: hidden;
    }

    .audio-preview {
      width: 100%;
      max-width: 600px;
      
      .audio-container {
        background-color: #fff;
        border-radius: 12px;
        padding: 40px;
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);

        .audio-cover {
          display: flex;
          justify-content: center;
          align-items: center;
          width: 200px;
          height: 200px;
          margin: 0 auto 30px;
          background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
          border-radius: 50%;
          
          i {
            font-size: 80px;
            color: #fff;
          }
        }

        .audio-info {
          text-align: center;

          h3 {
            font-size: 18px;
            color: #303133;
            margin-bottom: 20px;
            word-break: break-all;
          }

          audio {
            outline: none;
          }
        }
      }
    }

    .pdf-preview,
    .office-preview {
      width: 100%;
      height: 100%;
      background-color: #fff;
      border-radius: 8px;
      overflow: hidden;
    }

    .text-preview {
      width: 100%;
      max-width: 1000px;
      background-color: #fff;
      border-radius: 8px;
      padding: 24px;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);

      pre {
        margin: 0;
        font-family: 'Courier New', Courier, monospace;
        font-size: 14px;
        line-height: 1.6;
        color: #303133;
        white-space: pre-wrap;
        word-wrap: break-word;
      }
    }

    .unsupported-preview {
      width: 100%;
      height: 100%;
      display: flex;
      justify-content: center;
      align-items: center;

      .unsupported-content {
        text-align: center;
        color: #909399;

        i {
          font-size: 80px;
          margin-bottom: 20px;
        }

        p {
          font-size: 16px;
          margin-bottom: 24px;
        }
      }
    }
  }
}
</style>

