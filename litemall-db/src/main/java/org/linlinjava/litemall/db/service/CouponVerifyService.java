package org.linlinjava.litemall.db.service;

import org.linlinjava.litemall.db.domain.LitemallCart;
import org.linlinjava.litemall.db.domain.LitemallCoupon;
import org.linlinjava.litemall.db.domain.LitemallCouponUser;
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.util.CouponConstant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@Service
public class CouponVerifyService {

    @Autowired
    private LitemallCouponUserService couponUserService;
    @Autowired
    private LitemallCouponService couponService;
    @Autowired
    private LitemallGoodsService goodsService;

    /**
     * 检测优惠券是否适合
     *
     * @param userId
     * @param couponId
     * @param checkedGoodsPrice
     * @return
     */
    public LitemallCoupon checkCoupon(Integer userId, Integer couponId, Integer userCouponId, BigDecimal checkedGoodsPrice, List<LitemallCart> cartList) {
        LitemallCoupon coupon = couponService.findById(couponId);
        if (coupon == null || coupon.getDeleted()) {
            return null;
        }

        LitemallCouponUser couponUser = findCouponUser(userId, couponId, userCouponId);
        if (couponUser == null) {
            return null;
        }
        if (!isTimeValid(coupon, couponUser)) {
            return null;
        }
        if (!isGoodsTypeValid(coupon, cartList)) {
            return null;
        }
        if (!CouponConstant.STATUS_NORMAL.equals(coupon.getStatus())) {
            return null;
        }
        if (checkedGoodsPrice.compareTo(coupon.getMin()) < 0) {
            return null;
        }
        return coupon;
    }

    private LitemallCouponUser findCouponUser(Integer userId, Integer couponId, Integer userCouponId) {
        LitemallCouponUser couponUser = couponUserService.findById(userCouponId);
        if (couponUser == null) {
            return couponUserService.queryOne(userId, couponId);
        }
        if (!couponId.equals(couponUser.getCouponId()) || !userId.equals(couponUser.getUserId())) {
            return null;
        }
        return couponUser;
    }

    private boolean isTimeValid(LitemallCoupon coupon, LitemallCouponUser couponUser) {
        Short timeType = coupon.getTimeType();
        LocalDateTime now = LocalDateTime.now();
        if (CouponConstant.TIME_TYPE_TIME.equals(timeType)) {
            return !now.isBefore(coupon.getStartTime()) && !now.isAfter(coupon.getEndTime());
        }
        if (CouponConstant.TIME_TYPE_DAYS.equals(timeType)) {
            LocalDateTime expired = couponUser.getAddTime().plusDays(coupon.getDays());
            return !now.isAfter(expired);
        }
        return false;
    }

    private boolean isGoodsTypeValid(LitemallCoupon coupon, List<LitemallCart> cartList) {
        Short goodType = coupon.getGoodsType();
        if (!CouponConstant.GOODS_TYPE_CATEGORY.equals(goodType)
                && !CouponConstant.GOODS_TYPE_ARRAY.equals(goodType)) {
            return true;
        }
        Map<Integer, List<LitemallCart>> cartMap = buildCartMap(goodType, cartList);
        List<Integer> goodsValueList = new ArrayList<>(Arrays.asList(coupon.getGoodsValue()));
        goodsValueList.retainAll(cartMap.keySet());
        if (goodsValueList.isEmpty()) {
            return false;
        }
        BigDecimal total = sumCartPrice(goodsValueList, cartMap);
        return total.compareTo(coupon.getMin()) >= 0;
    }

    private Map<Integer, List<LitemallCart>> buildCartMap(Short goodType, List<LitemallCart> cartList) {
        Map<Integer, List<LitemallCart>> cartMap = new HashMap<>();
        for (LitemallCart cart : cartList) {
            Integer key = CouponConstant.GOODS_TYPE_ARRAY.equals(goodType)
                    ? cart.getGoodsId() : categoryIdOf(cart.getGoodsId());
            if (key == null) {
                continue;
            }
            cartMap.computeIfAbsent(key, k -> new LinkedList<>()).add(cart);
        }
        return cartMap;
    }

    private Integer categoryIdOf(Integer goodsId) {
        LitemallGoods goods = goodsService.findById(goodsId);
        return goods == null ? null : goods.getCategoryId();
    }

    private BigDecimal sumCartPrice(List<Integer> goodsValueList, Map<Integer, List<LitemallCart>> cartMap) {
        BigDecimal total = new BigDecimal(0);
        for (Integer goodsId : goodsValueList) {
            for (LitemallCart cart : cartMap.get(goodsId)) {
                total = total.add(cart.getPrice().multiply(new BigDecimal(cart.getNumber())));
            }
        }
        return total;
    }

}
