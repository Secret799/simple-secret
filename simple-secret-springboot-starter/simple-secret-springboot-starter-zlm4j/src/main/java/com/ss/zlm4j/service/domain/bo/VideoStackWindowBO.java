package com.ss.zlm4j.service.domain.bo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * VideoStackWindowParam对象，拼接屏幕地址参数
 */
@Data
@Accessors(chain = true)
public class VideoStackWindowBO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1;

    /**
     * 拼接视频地址
     */
    private String videoUrl;

    /**
     * 拼接图片地址,和videoUrl二选一
     */
    private String imgUrl;

    /**
     * 默认填充颜色
     */
    private String fillColor = "BFBFBF";

    /**
     * 所占的格子
     */
    private List<Integer> span;

}