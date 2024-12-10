package com.imooc.seckill.mapper;

import com.imooc.commons.model.pojo.SeckillVouchers;
import org.apache.ibatis.annotations.*;

/**
 * 秒杀代金券 Mapper
 */
public interface SeckillVouchersMapper {

    // 新增秒杀活动
    @Insert("insert into t_seckill_vouchers (fk_voucher_id, amount, start_time, end_time, is_valid, create_date, update_date) " +
            " values (#{fkVoucherId}, #{amount}, #{startTime}, #{endTime}, 1, now(), now())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int save(SeckillVouchers seckillVouchers);

    // 根据代金券 ID 查询秒杀活动
    @Select("select id, fk_voucher_id, amount, start_time, end_time, is_valid, create_date,update_date" +
            " from t_seckill_vouchers where fk_voucher_id = #{voucherId}")
    SeckillVouchers selectVoucher(Integer voucherId);

    // 更新秒杀活动信息
    @Update("update t_seckill_vouchers set amount = #{amount}, start_time = #{startTime}, " +
            "end_time = #{endTime}, is_valid = #{isValid}, update_date = now() " +
            "where fk_voucher_id = #{fkVoucherId}")
    int update(SeckillVouchers seckillVouchers);

    // 删除秒杀活动（根据代金券 ID）
    @Delete("delete from t_seckill_vouchers where fk_voucher_id = #{voucherId}")
    int deleteByVoucherId(Integer voucherId);
}
