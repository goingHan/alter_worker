package com.alertworker.common.mapper;

import com.alertworker.common.entity.DeadLetterMessage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DeadLetterMessageMapper extends BaseMapper<DeadLetterMessage> {
}
