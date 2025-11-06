package com.gzu.disk.service;


import com.gzu.disk.domain.BackChunk;
import com.gzu.disk.domain.BackFilelist;
import com.gzu.disk.domain.vo.CheckChunkVO;

import javax.servlet.http.HttpServletResponse;

public interface IBackFileService {

    int postFileUpload(BackChunk chunk, HttpServletResponse response);

    CheckChunkVO getFileUpload(BackChunk chunk, HttpServletResponse response);

    int deleteBackFileByIds(Long id);

    String mergeFile(BackFilelist fileInfo);
}