package com.gzu.web.controller.system;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import com.gzu.common.core.controller.BaseController;
import com.gzu.common.core.domain.AjaxResult;
import com.gzu.common.core.domain.model.RegisterBody;
import com.gzu.common.utils.StringUtils;
import com.gzu.framework.web.service.SysRegisterService;
import com.gzu.system.service.ISysConfigService;

/**
 * 注册验证
 * 
 * @author ruoyi
 */
@Api("注册验证")
@RestController
public class SysRegisterController extends BaseController
{
    @Autowired
    private SysRegisterService registerService;

    @Autowired
    private ISysConfigService configService;

    @PostMapping("/register")
    public AjaxResult register(@RequestBody RegisterBody user)
    {
        if (!("true".equals(configService.selectConfigByKey("sys.account.registerUser"))))
        {
            return error("当前系统没有开启注册功能！");
        }
        String msg = registerService.register(user);
        return StringUtils.isEmpty(msg) ? success() : error(msg);
    }

    @ApiOperation("c端注册")
    @PostMapping("/customer/register")
    public AjaxResult customerRegister(@RequestBody RegisterBody user)
    {
        if (!("true".equals(configService.selectConfigByKey("sys.account.registerUser"))))
        {
            return error("当前系统没有开启注册功能！");
        }
        String msg = registerService.customerRegister(user);
        return StringUtils.isEmpty(msg) ? success() : error(msg);
    }
}
