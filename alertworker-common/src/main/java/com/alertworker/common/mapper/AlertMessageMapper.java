package com.alertworker.common.mapper;

import com.alertworker.common.entity.AlertMessage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AlertMessageMapper extends BaseMapper<AlertMessage> {
}
