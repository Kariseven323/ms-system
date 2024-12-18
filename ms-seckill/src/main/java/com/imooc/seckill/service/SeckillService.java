package com.imooc.seckill.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.IdUtil;
import com.imooc.commons.constant.ApiConstant;
import com.imooc.commons.constant.RedisKeyConstant;
import com.imooc.commons.exception.ParameterException;
import com.imooc.commons.model.domain.ResultInfo;
import com.imooc.commons.model.pojo.SeckillVouchers;
import com.imooc.commons.model.pojo.VoucherOrders;
import com.imooc.commons.model.vo.SignInDinerInfo;
import com.imooc.commons.utils.AssertUtil;
import com.imooc.commons.utils.ResultInfoUtil;
import com.imooc.seckill.mapper.SeckillVouchersMapper;
import com.imooc.seckill.mapper.VoucherOrdersMapper;
import com.imooc.seckill.model.RedisLock;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 秒杀业务逻辑层
 */
@Service
public class SeckillService {

    @Resource
    private SeckillVouchersMapper seckillVouchersMapper;
    @Resource
    private VoucherOrdersMapper voucherOrdersMapper;
    @Value("${service.name.ms-oauth-server}")
    private String oauthServerName;
    @Resource
    private RestTemplate restTemplate;
    @Resource
    private RedisTemplate redisTemplate;
    @Resource
    private DefaultRedisScript defaultRedisScript;
    @Resource
    private RedisLock redisLock;
    @Resource
    private RedissonClient redissonClient;

    /**
     * 抢购代金券
     *
     * @param voucherId   代金券 ID
     * @param accessToken 登录token
     * @Para path 访问路径
     */
    @Transactional(rollbackFor = Exception.class)
    public ResultInfo doSeckill(Integer voucherId, String accessToken, String path) {
        // 基本参数校验
        AssertUtil.isTrue(voucherId == null || voucherId < 0, "请选择需要抢购的代金券");
        AssertUtil.isNotEmpty(accessToken, "请登录");

        // 注释原始的 关系型数据库 的流程
        // 判断此代金券是否加入抢购
        // SeckillVouchers seckillVouchers = seckillVouchersMapper.selectVoucher(voucherId);
        // AssertUtil.isTrue(seckillVouchers == null, "该代金券并未有抢购活动");
        // 判断是否有效
        // AssertUtil.isTrue(seckillVouchers.getIsValid() == 0, "该活动已结束");

        // 采用 Redis
        String key = RedisKeyConstant.seckill_vouchers.getKey() + voucherId;
        Map<String, Object> map = redisTemplate.opsForHash().entries(key);
        SeckillVouchers seckillVouchers = BeanUtil.mapToBean(map, SeckillVouchers.class, true, null);

        // 判断是否开始、结束
        Date now = new Date();
        AssertUtil.isTrue(now.before(seckillVouchers.getStartTime()), "该抢购还未开始");
        AssertUtil.isTrue(now.after(seckillVouchers.getEndTime()), "该抢购已结束");
        // 判断是否卖完
        AssertUtil.isTrue(seckillVouchers.getAmount() < 1, "该券已经卖完了");
        // 获取登录用户信息
        String url = oauthServerName + "user/me?access_token={accessToken}";
        ResultInfo resultInfo = restTemplate.getForObject(url, ResultInfo.class, accessToken);
        if (resultInfo.getCode() != ApiConstant.SUCCESS_CODE) {
            resultInfo.setPath(path);
            return resultInfo;
        }
        // 这里的data是一个LinkedHashMap，SignInDinerInfo
        SignInDinerInfo dinerInfo = BeanUtil.fillBeanWithMap((LinkedHashMap) resultInfo.getData(),
                new SignInDinerInfo(), false);
        // 判断登录用户是否已抢到(一个用户针对这次活动只能买一次)
        VoucherOrders order = voucherOrdersMapper.findDinerOrder(dinerInfo.getId(),
                seckillVouchers.getFkVoucherId());
        AssertUtil.isTrue(order != null, "该用户已抢到该代金券，无需再抢");

        // 注释原始的 关系型数据库 的流程
        // 扣库存
        // int count = seckillVouchersMapper.stockDecrease(seckillVouchers.getId());
        // AssertUtil.isTrue(count == 0, "该券已经卖完了");

        // 使用 Redis 锁一个账号只能购买一次
        String lockName = RedisKeyConstant.lock_key.getKey()
                + dinerInfo.getId() + ":" + voucherId;
        long expireTime = seckillVouchers.getEndTime().getTime() - now.getTime();

        // 自定义 Redis 分布式锁
        //String lockKey = redisLock.tryLock(lockName, expireTime);

        // Redisson 分布式锁
        RLock lock = redissonClient.getLock(lockName);

        try {
            // 不为空意味着拿到锁了，执行下单
            // 自定义 Redis 分布式锁处理
            // if (StrUtil.isNotBlank(lockKey)) {

            // Redisson 分布式锁处理
            boolean isLocked = lock.tryLock(expireTime, TimeUnit.MILLISECONDS);
            if (isLocked) {
                // 下单
                VoucherOrders voucherOrders = new VoucherOrders();
                voucherOrders.setFkDinerId(dinerInfo.getId());
                // Redis 中不需要维护外键信息
                // voucherOrders.setFkSeckillId(seckillVouchers.getId());
                voucherOrders.setFkVoucherId(seckillVouchers.getFkVoucherId());
                String orderNo = IdUtil.getSnowflake(1, 1).nextIdStr();
                voucherOrders.setOrderNo(orderNo);
                voucherOrders.setOrderType(1);
                voucherOrders.setStatus(0);
                long count = voucherOrdersMapper.save(voucherOrders);
                AssertUtil.isTrue(count == 0, "用户抢购失败");

                // 采用 Redis
                // 扣库存
                // count = redisTemplate.opsForHash().increment(key, "amount", -1);
                // AssertUtil.isTrue(count < 0, "该券已经卖完了");

                // 采用 Redis + Lua 解决问题
                // 扣库存
                List<String> keys = new ArrayList<>();
                keys.add(key);
                keys.add("amount");
                Long amount = (Long) redisTemplate.execute(defaultRedisScript, keys);
                AssertUtil.isTrue(amount == null || amount < 1, "该券已经卖完了");
            }
        } catch (Exception e) {
            // 手动回滚事务
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            // 自定义 Redis 解锁
            // redisLock.unlock(lockName, lockKey);

            // Redisson 解锁
            lock.unlock();
            if (e instanceof ParameterException) {
                return ResultInfoUtil.buildError(0, "该券已经卖完了", path);
            }
        }

        return ResultInfoUtil.buildSuccess(path, "抢购成功");
    }

    /**
     * 添加需要抢购的代金券
     *
     * @param seckillVouchers
     */
    @Transactional(rollbackFor = Exception.class)
    public void addSeckillVouchers(SeckillVouchers seckillVouchers) {
        // 非空校验
        AssertUtil.isTrue(seckillVouchers.getFkVoucherId() == null, "请选择需要抢购的代金券");
        AssertUtil.isTrue(seckillVouchers.getAmount() == 0, "请输入抢购总数量");
        Date now = new Date();
        AssertUtil.isNotNull(seckillVouchers.getStartTime(), "请输入开始时间");
        // 生产环境下面一行代码需放行，这里注释方便测试
        // AssertUtil.isTrue(now.after(seckillVouchers.getStartTime()), "开始时间不能早于当前时间");
        AssertUtil.isNotNull(seckillVouchers.getEndTime(), "请输入结束时间");
        AssertUtil.isTrue(now.after(seckillVouchers.getEndTime()), "结束时间不能早于当前时间");
        AssertUtil.isTrue(seckillVouchers.getStartTime().after(seckillVouchers.getEndTime()), "开始时间不能晚于结束时间");

        // 注释原始的走 关系型数据库 的流程
        // 验证数据库中是否已经存在该券的秒杀活动
        // SeckillVouchers seckillVouchersFromDb = seckillVouchersMapper.selectVoucher(seckillVouchers.getFkVoucherId());
        // AssertUtil.isTrue(seckillVouchersFromDb != null, "该券已经拥有了抢购活动");
        // 插入数据库
        // seckillVouchersMapper.save(seckillVouchers);

        // 采用 Redis 实现
        String key = RedisKeyConstant.seckill_vouchers.getKey() + seckillVouchers.getFkVoucherId();
        // 验证 Redis 中是否已经存在该券的秒杀活动
        Map<String, Object> map = redisTemplate.opsForHash().entries(key);
        AssertUtil.isTrue(!map.isEmpty() && (int) map.get("amount") > 0, "该券已经拥有了抢购活动");

        // 插入 Redis
        seckillVouchers.setIsValid(1);
        seckillVouchers.setCreateDate(now);
        seckillVouchers.setUpdateDate(now);
        redisTemplate.opsForHash().putAll(key, BeanUtil.beanToMap(seckillVouchers));
    }

    @Scheduled(fixedRate = 60000) // 每隔60秒执行一次
    public void syncRedisToDatabase() {
        // 获取所有 Redis 中的秒杀活动键名
        Set<String> keys = redisTemplate.keys(RedisKeyConstant.seckill_vouchers.getKey() + "*");

        if (keys == null || keys.isEmpty()) {
            return; // 无秒杀活动，无需同步
        }

        for (String key : keys) {
            // 从 Redis 获取代金券数据
            Map<String, Object> map = redisTemplate.opsForHash().entries(key);
            if (map.isEmpty()) {
                continue; // 跳过无数据的活动
            }

            // 将 Redis 数据转换为 SeckillVouchers 对象
            SeckillVouchers seckillVouchers = BeanUtil.mapToBean(map, SeckillVouchers.class, true, null);

            try {
                // 查询数据库中是否已有该活动
                SeckillVouchers existing = seckillVouchersMapper.selectVoucher(seckillVouchers.getFkVoucherId());
                if (existing == null) {
                    // 如果数据库中没有该活动，则插入
                    seckillVouchersMapper.save(seckillVouchers);
                } else {
                    // 如果数据库中已有该活动，则更新
                    seckillVouchersMapper.update(seckillVouchers);
                }
            } catch (Exception e) {
                // 记录错误日志，避免定时任务中断
                System.err.println("同步代金券数据出错：" + e.getMessage());
            }
        }
    }

    /**
     * 修改秒杀活动
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateSeckillVouchers(SeckillVouchers seckillVouchers) {
        // 校验参数
        AssertUtil.isTrue(seckillVouchers.getFkVoucherId() == null, "请选择需要修改的代金券");
        AssertUtil.isTrue(seckillVouchers.getAmount() < 0, "库存不能小于0");
        // 更新秒杀活动
        int result = seckillVouchersMapper.update(seckillVouchers);
        AssertUtil.isTrue(result == 0, "修改秒杀活动失败");
    }

    /**
     * 查询秒杀活动
     */
    public SeckillVouchers querySeckillVoucher(Integer voucherId) {
        AssertUtil.isTrue(voucherId == null, "代金券ID不能为空");
        SeckillVouchers seckillVouchers = seckillVouchersMapper.selectVoucher(voucherId);
        AssertUtil.isTrue(seckillVouchers == null, "秒杀活动不存在");
        return seckillVouchers;
    }

    /**
     * 删除秒杀活动
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteSeckillVoucher(Integer voucherId) {
        AssertUtil.isTrue(voucherId == null, "代金券ID不能为空");

        // 删除 MySQL 中的秒杀活动
        int result = seckillVouchersMapper.deleteByVoucherId(voucherId);
        AssertUtil.isTrue(result == 0, "删除秒杀活动失败");

        // 删除 Redis 中的相关数据
        String redisKey = RedisKeyConstant.seckill_vouchers.getKey() + voucherId;
        Boolean isDeleted = redisTemplate.delete(redisKey);
        AssertUtil.isTrue(isDeleted, "删除 Redis 中的秒杀活动成功");
    }

    // 批量新增秒杀活动
    public ResultInfo<String> addSeckillVouchersBatch(List<SeckillVouchers> seckillVouchersList) {
        if (seckillVouchersList == null || seckillVouchersList.isEmpty()) {
            return ResultInfoUtil.buildError("传入的秒杀活动列表为空");
        }

        int batchSize = 500; // 每批次插入 500 条
        int total = seckillVouchersList.size();
        int pages = (total + batchSize - 1) / batchSize; // 计算总页数

        Date now = new Date(); // 提前创建时间对象，避免重复调用

        for (int i = 0; i < pages; i++) {
            int fromIndex = i * batchSize;
            int toIndex = Math.min((i + 1) * batchSize, total);
            List<SeckillVouchers> batchList = seckillVouchersList.subList(fromIndex, toIndex);

            // 为每个秒杀活动对象设置必要的字段
            batchList.forEach(seckillVouchers -> {
                seckillVouchers.setIsValid(1); // 设置有效状态
                seckillVouchers.setCreateDate(now); // 设置创建时间
                seckillVouchers.setUpdateDate(now); // 设置更新时间
            });

            // 插入当前批次数据
            seckillVouchersMapper.saveBatch(batchList);

            // 插入到 Redis
            batchList.forEach(seckillVouchers -> {
                String key = RedisKeyConstant.seckill_vouchers.getKey() + seckillVouchers.getFkVoucherId();
                Map<String, Object> redisData = BeanUtil.beanToMap(seckillVouchers);
                redisTemplate.opsForHash().putAll(key, redisData);
            });
        }

        return ResultInfoUtil.buildSuccess("批量添加秒杀活动成功");
    }
}