package com.highestpeak.entity;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ImageVo {
    /**
     * image的全局唯一id，应该每个图片都可以唯一生成
     */
    private String id;
    private String url;
    private String desc;

    private String localImageFilePath;
}
