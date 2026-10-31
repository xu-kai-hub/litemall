package org.linlinjava.litemall.db.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.linlinjava.litemall.db.domain.LitemallCart;
import org.linlinjava.litemall.db.domain.LitemallCoupon;
import org.linlinjava.litemall.db.domain.LitemallCouponUser;
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.util.CouponConstant;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponVerifyServiceTest {

    @Mock
    private LitemallCouponUserService couponUserService;
    @Mock
    private LitemallCouponService couponService;
    @Mock
    private LitemallGoodsService goodsService;
    @InjectMocks
    private CouponVerifyService couponVerifyService;

    private LitemallCoupon createCoupon(Short timeType, Short days, LocalDateTime startTime, LocalDateTime endTime,
                                        Short goodsType, Integer[] goodsValue, BigDecimal min, Short status) {
        LitemallCoupon coupon = new LitemallCoupon();
        coupon.setId(1);
        coupon.setDeleted(false);
        coupon.setTimeType(timeType);
        coupon.setDays(days);
        coupon.setStartTime(startTime);
        coupon.setEndTime(endTime);
        coupon.setGoodsType(goodsType);
        coupon.setGoodsValue(goodsValue);
        coupon.setMin(min);
        coupon.setStatus(status);
        return coupon;
    }

    private LitemallCouponUser createCouponUser(Integer couponId, LocalDateTime addTime) {
        LitemallCouponUser couponUser = new LitemallCouponUser();
        couponUser.setId(1);
        couponUser.setUserId(1);
        couponUser.setCouponId(couponId);
        couponUser.setAddTime(addTime);
        return couponUser;
    }

    private LitemallCart createCart(Integer goodsId, BigDecimal price, Short number) {
        LitemallCart cart = new LitemallCart();
        cart.setGoodsId(goodsId);
        cart.setPrice(price);
        cart.setNumber(number);
        return cart;
    }

    @Test
    void testCheckCoupon_normalAvailable() {
        LitemallCoupon coupon = createCoupon(CouponConstant.TIME_TYPE_TIME, null,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1),
                CouponConstant.GOODS_TYPE_ALL, new Integer[]{}, new BigDecimal("100"), CouponConstant.STATUS_NORMAL);
        when(couponService.findById(1)).thenReturn(coupon);
        when(couponUserService.findById(1)).thenReturn(createCouponUser(1, LocalDateTime.now()));

        LitemallCoupon result = couponVerifyService.checkCoupon(1, 1, 1, new BigDecimal("150"),
                Collections.emptyList());

        assertNotNull(result);
        assertEquals(coupon, result);
    }

    @Test
    void testCheckCoupon_couponNotFound() {
        when(couponService.findById(1)).thenReturn(null);

        LitemallCoupon result = couponVerifyService.checkCoupon(1, 1, 1, new BigDecimal("150"),
                Collections.emptyList());

        assertNull(result);
    }

    @Test
    void testCheckCoupon_couponDeleted() {
        LitemallCoupon coupon = createCoupon(CouponConstant.TIME_TYPE_TIME, null,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1),
                CouponConstant.GOODS_TYPE_ALL, new Integer[]{}, new BigDecimal("100"), CouponConstant.STATUS_NORMAL);
        coupon.setDeleted(true);
        when(couponService.findById(1)).thenReturn(coupon);

        LitemallCoupon result = couponVerifyService.checkCoupon(1, 1, 1, new BigDecimal("150"),
                Collections.emptyList());

        assertNull(result);
        verifyNoInteractions(couponUserService);
    }

    @Test
    void testCheckCoupon_userCouponNotFound() {
        LitemallCoupon coupon = createCoupon(CouponConstant.TIME_TYPE_TIME, null,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1),
                CouponConstant.GOODS_TYPE_ALL, new Integer[]{}, new BigDecimal("100"), CouponConstant.STATUS_NORMAL);
        when(couponService.findById(1)).thenReturn(coupon);
        when(couponUserService.findById(1)).thenReturn(null);
        when(couponUserService.queryOne(1, 1)).thenReturn(null);

        LitemallCoupon result = couponVerifyService.checkCoupon(1, 1, 1, new BigDecimal("150"),
                Collections.emptyList());

        assertNull(result);
    }

    @Test
    void testCheckCoupon_userCouponIdMismatch() {
        LitemallCoupon coupon = createCoupon(CouponConstant.TIME_TYPE_TIME, null,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1),
                CouponConstant.GOODS_TYPE_ALL, new Integer[]{}, new BigDecimal("100"), CouponConstant.STATUS_NORMAL);
        when(couponService.findById(1)).thenReturn(coupon);
        when(couponUserService.findById(1)).thenReturn(createCouponUser(2, LocalDateTime.now()));

        LitemallCoupon result = couponVerifyService.checkCoupon(1, 1, 1, new BigDecimal("150"),
                Collections.emptyList());

        assertNull(result);
    }

    @Test
    void testCheckCoupon_expiredByTime() {
        LitemallCoupon coupon = createCoupon(CouponConstant.TIME_TYPE_TIME, null,
                LocalDateTime.now().minusDays(2), LocalDateTime.now().minusDays(1),
                CouponConstant.GOODS_TYPE_ALL, new Integer[]{}, new BigDecimal("100"), CouponConstant.STATUS_NORMAL);
        when(couponService.findById(1)).thenReturn(coupon);
        when(couponUserService.findById(1)).thenReturn(createCouponUser(1, LocalDateTime.now()));

        LitemallCoupon result = couponVerifyService.checkCoupon(1, 1, 1, new BigDecimal("150"),
                Collections.emptyList());

        assertNull(result);
    }

    @Test
    void testCheckCoupon_expiredByDays() {
        LitemallCoupon coupon = createCoupon(CouponConstant.TIME_TYPE_DAYS, (short) 7, null, null,
                CouponConstant.GOODS_TYPE_ALL, new Integer[]{}, new BigDecimal("100"), CouponConstant.STATUS_NORMAL);
        when(couponService.findById(1)).thenReturn(coupon);
        when(couponUserService.findById(1)).thenReturn(createCouponUser(1, LocalDateTime.now().minusDays(8)));

        LitemallCoupon result = couponVerifyService.checkCoupon(1, 1, 1, new BigDecimal("150"),
                Collections.emptyList());

        assertNull(result);
    }

    @Test
    void testCheckCoupon_invalidTimeType() {
        LitemallCoupon coupon = createCoupon((short) 99, null, null, null,
                CouponConstant.GOODS_TYPE_ALL, new Integer[]{}, new BigDecimal("100"), CouponConstant.STATUS_NORMAL);
        when(couponService.findById(1)).thenReturn(coupon);
        when(couponUserService.findById(1)).thenReturn(createCouponUser(1, LocalDateTime.now()));

        LitemallCoupon result = couponVerifyService.checkCoupon(1, 1, 1, new BigDecimal("150"),
                Collections.emptyList());

        assertNull(result);
    }

    @Test
    void testCheckCoupon_goodsTypeArrayMatching() {
        LitemallCoupon coupon = createCoupon(CouponConstant.TIME_TYPE_TIME, null,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1),
                CouponConstant.GOODS_TYPE_ARRAY, new Integer[]{1, 2}, new BigDecimal("100"), CouponConstant.STATUS_NORMAL);
        when(couponService.findById(1)).thenReturn(coupon);
        when(couponUserService.findById(1)).thenReturn(createCouponUser(1, LocalDateTime.now()));

        LitemallCart cart1 = createCart(1, new BigDecimal("60"), (short) 1);
        LitemallCart cart2 = createCart(2, new BigDecimal("50"), (short) 1);
        List<LitemallCart> cartList = Arrays.asList(cart1, cart2);

        LitemallCoupon result = couponVerifyService.checkCoupon(1, 1, 1, new BigDecimal("110"),
                cartList);

        assertNotNull(result);
        assertEquals(coupon, result);
    }

    @Test
    void testCheckCoupon_categoryMatchingButNoCartInCategory() {
        LitemallCoupon coupon = createCoupon(CouponConstant.TIME_TYPE_TIME, null,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1),
                CouponConstant.GOODS_TYPE_CATEGORY, new Integer[]{1}, new BigDecimal("100"), CouponConstant.STATUS_NORMAL);
        when(couponService.findById(1)).thenReturn(coupon);
        when(couponUserService.findById(1)).thenReturn(createCouponUser(1, LocalDateTime.now()));

        LitemallCart cart = createCart(2, new BigDecimal("150"), (short) 1);
        LitemallGoods goods = new LitemallGoods();
        goods.setCategoryId(2);
        when(goodsService.findById(2)).thenReturn(goods);

        LitemallCoupon result = couponVerifyService.checkCoupon(1, 1, 1, new BigDecimal("150"),
                Collections.singletonList(cart));

        assertNull(result);
    }

    @Test
    void testCheckCoupon_goodsAmountBelowMin() {
        LitemallCoupon coupon = createCoupon(CouponConstant.TIME_TYPE_TIME, null,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1),
                CouponConstant.GOODS_TYPE_ARRAY, new Integer[]{1}, new BigDecimal("100"), CouponConstant.STATUS_NORMAL);
        when(couponService.findById(1)).thenReturn(coupon);
        when(couponUserService.findById(1)).thenReturn(createCouponUser(1, LocalDateTime.now()));

        LitemallCart cart = createCart(1, new BigDecimal("50"), (short) 1);
        List<LitemallCart> cartList = Collections.singletonList(cart);

        LitemallCoupon result = couponVerifyService.checkCoupon(1, 1, 1, new BigDecimal("50"),
                cartList);

        assertNull(result);
    }

    @Test
    void testCheckCoupon_couponStatusNotNormal() {
        LitemallCoupon coupon = createCoupon(CouponConstant.TIME_TYPE_TIME, null,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1),
                CouponConstant.GOODS_TYPE_ALL, new Integer[]{}, new BigDecimal("100"), CouponConstant.STATUS_EXPIRED);
        when(couponService.findById(1)).thenReturn(coupon);
        when(couponUserService.findById(1)).thenReturn(createCouponUser(1, LocalDateTime.now()));

        LitemallCoupon result = couponVerifyService.checkCoupon(1, 1, 1, new BigDecimal("150"),
                Collections.emptyList());

        assertNull(result);
    }

    @Test
    void testCheckCoupon_orderTotalBelowMin() {
        LitemallCoupon coupon = createCoupon(CouponConstant.TIME_TYPE_TIME, null,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1),
                CouponConstant.GOODS_TYPE_ALL, new Integer[]{}, new BigDecimal("100"), CouponConstant.STATUS_NORMAL);
        when(couponService.findById(1)).thenReturn(coupon);
        when(couponUserService.findById(1)).thenReturn(createCouponUser(1, LocalDateTime.now()));

        LitemallCoupon result = couponVerifyService.checkCoupon(1, 1, 1, new BigDecimal("99"),
                Collections.emptyList());

        assertNull(result);
    }

    @Test
    void testCheckCoupon_reachMinAvailable() {
        LitemallCoupon coupon = createCoupon(CouponConstant.TIME_TYPE_TIME, null,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1),
                CouponConstant.GOODS_TYPE_ALL, new Integer[]{}, new BigDecimal("100"), CouponConstant.STATUS_NORMAL);
        when(couponService.findById(1)).thenReturn(coupon);
        when(couponUserService.findById(1)).thenReturn(createCouponUser(1, LocalDateTime.now()));

        LitemallCoupon result = couponVerifyService.checkCoupon(1, 1, 1, new BigDecimal("100"),
                Collections.emptyList());

        assertNotNull(result);
        assertEquals(coupon, result);
    }

    // PIT survivor 1: a null coupon must stop before touching the coupon user table
    @Test
    void testCheckCoupon_couponNotFoundSkipsCouponUser() {
        when(couponService.findById(1)).thenReturn(null);

        LitemallCoupon result = couponVerifyService.checkCoupon(1, 1, 1, new BigDecimal("150"),
                Collections.emptyList());

        assertNull(result);
        verifyNoInteractions(couponUserService);
    }

    // PIT survivor 2: a day based coupon that has not expired must still be usable
    @Test
    void testCheckCoupon_daysNotExpired() {
        LitemallCoupon coupon = createCoupon(CouponConstant.TIME_TYPE_DAYS, (short) 7, null, null,
                CouponConstant.GOODS_TYPE_ALL, new Integer[]{}, new BigDecimal("100"), CouponConstant.STATUS_NORMAL);
        when(couponService.findById(1)).thenReturn(coupon);
        when(couponUserService.findById(1)).thenReturn(createCouponUser(1, LocalDateTime.now().minusDays(1)));

        LitemallCoupon result = couponVerifyService.checkCoupon(1, 1, 1, new BigDecimal("150"),
                Collections.emptyList());

        assertEquals(coupon, result);
    }

    // PIT survivor 2: a time window that has not started yet must be rejected
    @Test
    void testCheckCoupon_notStartedYet() {
        LitemallCoupon coupon = createCoupon(CouponConstant.TIME_TYPE_TIME, null,
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(2),
                CouponConstant.GOODS_TYPE_ALL, new Integer[]{}, new BigDecimal("100"), CouponConstant.STATUS_NORMAL);
        when(couponService.findById(1)).thenReturn(coupon);
        when(couponUserService.findById(1)).thenReturn(createCouponUser(1, LocalDateTime.now()));

        LitemallCoupon result = couponVerifyService.checkCoupon(1, 1, 1, new BigDecimal("150"),
                Collections.emptyList());

        assertNull(result);
    }

    // userCouponId is optional, fallback by userId and couponId
    @Test
    void testCheckCoupon_userCouponIdNullFallback() {
        LitemallCoupon coupon = createCoupon(CouponConstant.TIME_TYPE_TIME, null,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1),
                CouponConstant.GOODS_TYPE_ALL, new Integer[]{}, new BigDecimal("100"), CouponConstant.STATUS_NORMAL);
        when(couponService.findById(1)).thenReturn(coupon);
        when(couponUserService.queryOne(1, 1)).thenReturn(createCouponUser(1, LocalDateTime.now()));

        LitemallCoupon result = couponVerifyService.checkCoupon(1, 1, null, new BigDecimal("150"),
                Collections.emptyList());

        assertEquals(coupon, result);
    }

    // PIT survivor 3: goods in the coupon list but their subtotal is below min
    @Test
    void testCheckCoupon_goodsSubtotalBelowMin() {
        LitemallCoupon coupon = createCoupon(CouponConstant.TIME_TYPE_TIME, null,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1),
                CouponConstant.GOODS_TYPE_ARRAY, new Integer[]{1}, new BigDecimal("100"), CouponConstant.STATUS_NORMAL);
        when(couponService.findById(1)).thenReturn(coupon);
        when(couponUserService.findById(1)).thenReturn(createCouponUser(1, LocalDateTime.now()));

        LitemallCart cart = createCart(1, new BigDecimal("50"), (short) 1);

        LitemallCoupon result = couponVerifyService.checkCoupon(1, 1, 1, new BigDecimal("150"),
                Collections.singletonList(cart));

        assertNull(result);
    }

    // PIT survivor 3/4: no cart matches the coupon with min 0, still reject
    @Test
    void testCheckCoupon_noGoodsMatchWithZeroMin() {
        LitemallCoupon coupon = createCoupon(CouponConstant.TIME_TYPE_TIME, null,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1),
                CouponConstant.GOODS_TYPE_ARRAY, new Integer[]{9}, new BigDecimal("0"), CouponConstant.STATUS_NORMAL);
        when(couponService.findById(1)).thenReturn(coupon);
        when(couponUserService.findById(1)).thenReturn(createCouponUser(1, LocalDateTime.now()));

        LitemallCart cart = createCart(1, new BigDecimal("150"), (short) 1);

        LitemallCoupon result = couponVerifyService.checkCoupon(1, 1, 1, new BigDecimal("150"),
                Collections.singletonList(cart));

        assertNull(result);
    }

    // category coupon works, also covers two carts in the same category
    @Test
    void testCheckCoupon_categoryMatched() {
        LitemallCoupon coupon = createCoupon(CouponConstant.TIME_TYPE_TIME, null,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1),
                CouponConstant.GOODS_TYPE_CATEGORY, new Integer[]{2}, new BigDecimal("100"), CouponConstant.STATUS_NORMAL);
        when(couponService.findById(1)).thenReturn(coupon);
        when(couponUserService.findById(1)).thenReturn(createCouponUser(1, LocalDateTime.now()));

        LitemallGoods goods = new LitemallGoods();
        goods.setCategoryId(2);
        when(goodsService.findById(2)).thenReturn(goods);

        LitemallCart cart1 = createCart(2, new BigDecimal("60"), (short) 1);
        LitemallCart cart2 = createCart(2, new BigDecimal("50"), (short) 1);

        LitemallCoupon result = couponVerifyService.checkCoupon(1, 1, 1, new BigDecimal("110"),
                Arrays.asList(cart1, cart2));

        assertEquals(coupon, result);
    }
}
