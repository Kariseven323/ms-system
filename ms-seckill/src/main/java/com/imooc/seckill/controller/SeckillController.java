package com.imooc.seckill.controller;

import com.imooc.commons.model.domain.ResultInfo;
import com.imooc.commons.model.pojo.SeckillVouchers;
import com.imooc.commons.utils.ResultInfoUtil;
import com.imooc.seckill.service.SeckillService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 秒杀控制层
 */
@RestController
@RequestMapping("/seckill")
public class SeckillController {

    @Resource
    private SeckillService seckillService;
    @Resource
    private HttpServletRequest request;

    /**
     * 秒杀下单
     */
    @PostMapping("/{voucherId}")
    public ResultInfo<String> doSeckill(@PathVariable Integer voucherId,
                                        @RequestParam String access_token) {
        ResultInfo<String> resultInfo = seckillService.doSeckill(voucherId, access_token, request.getServletPath());
        return resultInfo;
    }

    /**
     * 新增秒杀活动
     */
    @PostMapping("/add")
    public ResultInfo<String> addSeckillVouchers(@RequestBody SeckillVouchers seckillVouchers) {
        seckillService.addSeckillVouchers(seckillVouchers);
        return ResultInfoUtil.buildSuccess(request.getServletPath(), "添加秒杀活动成功");
    }

    /**
     * 批量新增秒杀活动
     */
    @PostMapping("/addBatch")
    public ResultInfo<String> addSeckillVouchersBatch(@RequestBody List<SeckillVouchers> seckillVouchersList) {
        return seckillService.addSeckillVouchersBatch(seckillVouchersList);
    }

    /**
     * 修改秒杀活动
     */
    @PostMapping("/update")
    public ResultInfo<String> updateSeckillVouchers(@RequestBody SeckillVouchers seckillVouchers) {
        seckillService.updateSeckillVouchers(seckillVouchers);
        return ResultInfoUtil.buildSuccess(request.getServletPath(), "修改秒杀活动成功");
    }

    /**
     * 查询秒杀活动
     */
    @GetMapping("/query/{voucherId}")
    public ResultInfo<SeckillVouchers> querySeckillVoucher(@PathVariable Integer voucherId) {
        SeckillVouchers seckillVouchers = seckillService.querySeckillVoucher(voucherId);
        return ResultInfoUtil.buildSuccess(request.getServletPath(), seckillVouchers);
    }

    /**
     * 删除秒杀活动
     */
    @DeleteMapping("/delete/{voucherId}")
    public ResultInfo<String> deleteSeckillVoucher(@PathVariable Integer voucherId) {
        seckillService.deleteSeckillVoucher(voucherId);
        return ResultInfoUtil.buildSuccess(request.getServletPath(), "删除秒杀活动成功");
    }
}
