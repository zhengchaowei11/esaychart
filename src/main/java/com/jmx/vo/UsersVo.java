package com.jmx.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Users的VO对象,用于返回给前端
 */

@Data
@AllArgsConstructor  //包含所有参数的构造方法
@NoArgsConstructor     //没有参数的构造方法
public class UsersVo  {
    private String id;
    private String username;
    private String faceImage;
    private String faceImageBig;
    private String nickname;
    private String qrcode;
}
