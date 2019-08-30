package com.sanxin.cloud.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sanxin.cloud.entity.SysRoles;
import com.sanxin.cloud.mapper.SysRolesMapper;
import com.sanxin.cloud.service.SysRolesService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 * 系统角色表 服务实现类
 * </p>
 *
 * @author Arno
 * @since 2019-08-26
 */
@Service
public class SysRolesServiceImpl extends ServiceImpl<SysRolesMapper, SysRoles> implements SysRolesService {

    @Override
    public List<SysRoles> queryRoles(SysRoles roles) {
        return baseMapper.queryRoles(roles);
    }
}
