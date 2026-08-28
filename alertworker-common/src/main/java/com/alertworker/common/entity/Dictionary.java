package com.alertworker.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dictionary")
public class Dictionary extends BaseEntity {
    private String dictType;
    private String dictKey;
    private String dictValue;
}
