package com.ss.zlm4j.service.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 视频拼接屏业务对象
 *
 * @author JunPzx
 * @since 2025/8/20 18:15
 */
@Data
@Accessors(chain = true)
public class VideoStackBO implements Serializable {


    @Serial
    private static final long serialVersionUID = 1;

    /**
     * 拼接屏任务id(流id)
     */
    @NotBlank(message = "拼接屏任务id不为空")
    private String id;

    /**
     * 拼接屏任务流app
     */
    private String app = "live";

    /**
     * 推流地址，如果传了pushUrl将不在本地产生流
     */
    private String pushUrl;

    /**
     * 拼接屏行数
     */
    @NotNull(message = "拼接屏行数不为空")
    private Integer row;

    /**
     * 拼接屏列行数
     */
    @NotNull(message = "拼接屏列行数不为空")
    private Integer col;

    /**
     * 拼接屏宽度
     */
    @NotNull(message = "拼接屏宽度不为空")
    private Integer width;

    /**
     * 拼接屏高度
     */
    @NotNull(message = "拼接屏高度不为空")
    private Integer height;

    /**
     * 图片链接，为空则填灰色
     */
    private String fillImgUrl;

    /**
     * 默认填充颜色
     */
    private String fillColor = "BFBFBF";

    /**
     * 是否存在分割线
     */
    private Boolean gridLineEnable = false;

    /**
     * 分割线颜色
     */
    private String gridLineColor = "000000";

    /**
     * 分割线宽度
     */
    private Integer gridLineWidth = 1;

    /**
     * 拼接屏内容
     */
    private List<VideoStackWindowBO> windowList;


}
