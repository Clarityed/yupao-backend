package com.clarity.yupaobackend.model.request;

import lombok.Data;

import java.io.Serializable;

/**
 * 功能描述
 *
 * @author: clarity
 * @date: 2022年10月07日 18:35
 */
@Data
public class TeamDeleteRequest implements Serializable {

    private static final long serialVersionUID = 155897295232106835L;
    /**
     * 队伍 id
     */
    private Long teamId;

}
