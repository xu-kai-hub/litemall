package org.linlinjava.litemall.wx.web;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.linlinjava.litemall.core.system.SystemConfig;
import org.linlinjava.litemall.core.util.JacksonUtil;
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.db.domain.*;
import org.linlinjava.litemall.db.service.*;
import org.linlinjava.litemall.wx.annotation.LoginUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

import static org.linlinjava.litemall.wx.util.WxResponseCode.GOODS_NO_STOCK;
import static org.linlinjava.litemall.wx.util.WxResponseCode.GOODS_UNSHELVE;

/**
 * 用户购物车服务
 */
@RestController
@RequestMapping("/wx/cart")
@Validated
public class WxCartController {
    private final Log logger = LogFactory.getLog(WxCartController.class);

    @Autowired
    private LitemallCartService cartService;
    @Autowired
    private LitemallGoodsService goodsService;
    @Autowired
    private LitemallGoodsProductService productService;
    @Autowired
    private LitemallAddressService addressService;
    @Autowired
    private LitemallGrouponRulesService grouponRulesService;
    @Autowired
    private LitemallCouponService couponService;
    @Autowired
    private LitemallCouponUserService couponUserService;
    @Autowired
    private CouponVerifyService couponVerifyService;

    /**
     * 用户购物车信息
     *
     * @param userId 用户ID
     * @return 用户购物车信息
     */
    @GetMapping("index")
    public Object index(@LoginUser Integer userId) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }

        List<LitemallCart> list = cartService.queryByUid(userId);
        List<LitemallCart> cartList = new ArrayList<>();
        // TODO
        // 如果系统检查商品已删除或已下架，则系统自动删除。
        // 更好的效果应该是告知用户商品失效，允许用户点击按钮来清除失效商品。
        for (LitemallCart cart : list) {
            LitemallGoods goods = goodsService.findById(cart.getGoodsId());
            if (goods == null || !goods.getIsOnSale()) {
                cartService.deleteById(cart.getId());
                logger.debug("系统自动删除失效购物车商品 goodsId=" + cart.getGoodsId() + " productId=" + cart.getProductId());
            }
            else{
                cartList.add(cart);
            }
        }

        Integer goodsCount = 0;
        BigDecimal goodsAmount = new BigDecimal(0.00);
        Integer checkedGoodsCount = 0;
        BigDecimal checkedGoodsAmount = new BigDecimal(0.00);
        for (LitemallCart cart : cartList) {
            goodsCount += cart.getNumber();
            goodsAmount = goodsAmount.add(cart.getPrice().multiply(new BigDecimal(cart.getNumber())));
            if (cart.getChecked()) {
                checkedGoodsCount += cart.getNumber();
                checkedGoodsAmount = checkedGoodsAmount.add(cart.getPrice().multiply(new BigDecimal(cart.getNumber())));
            }
        }
        Map<String, Object> cartTotal = new HashMap<>();
        cartTotal.put("goodsCount", goodsCount);
        cartTotal.put("goodsAmount", goodsAmount);
        cartTotal.put("checkedGoodsCount", checkedGoodsCount);
        cartTotal.put("checkedGoodsAmount", checkedGoodsAmount);

        Map<String, Object> result = new HashMap<>();
        result.put("cartList", cartList);
        result.put("cartTotal", cartTotal);

        return ResponseUtil.ok(result);
    }

    /**
     * 加入商品到购物车
     * <p>
     * 如果已经存在购物车货品，则增加数量；
     * 否则添加新的购物车货品项。
     *
     * @param userId 用户ID
     * @param cart   购物车商品信息， { goodsId: xxx, productId: xxx, number: xxx }
     * @return 加入购物车操作结果
     */
    @PostMapping("add")
    public Object add(@LoginUser Integer userId, @RequestBody LitemallCart cart) {
        CartAddResult result = addToCart(userId, cart, true);
        if (result.error != null) {
            return result.error;
        }
        return goodscount(userId);
    }

    /**
     * 立即购买
     * <p>
     * 和add方法的区别在于：
     * 1. 如果购物车内已经存在购物车货品，前者的逻辑是数量添加，这里的逻辑是数量覆盖
     * 2. 添加成功以后，前者的逻辑是返回当前购物车商品数量，这里的逻辑是返回对应购物车项的ID
     *
     * @param userId 用户ID
     * @param cart   购物车商品信息， { goodsId: xxx, productId: xxx, number: xxx }
     * @return 立即购买操作结果
     */
    @PostMapping("fastadd")
    public Object fastadd(@LoginUser Integer userId, @RequestBody LitemallCart cart) {
        CartAddResult result = addToCart(userId, cart, false);
        if (result.error != null) {
            return result.error;
        }
        return ResponseUtil.ok(result.cartId);
    }

    private CartAddResult addToCart(Integer userId, LitemallCart cart, boolean merge) {
        if (userId == null) {
            return CartAddResult.error(ResponseUtil.unlogin());
        }
        if (cart == null) {
            return CartAddResult.error(ResponseUtil.badArgument());
        }
        Integer productId = cart.getProductId();
        Short number = cart.getNumber();
        Integer goodsId = cart.getGoodsId();
        if (!ObjectUtils.allNotNull(productId, number, goodsId)) {
            return CartAddResult.error(ResponseUtil.badArgument());
        }
        if (number <= 0) {
            return CartAddResult.error(ResponseUtil.badArgument());
        }

        LitemallGoods goods = goodsService.findById(goodsId);
        if (goods == null || !goods.getIsOnSale()) {
            return CartAddResult.error(ResponseUtil.fail(GOODS_UNSHELVE, "商品已下架"));
        }

        LitemallGoodsProduct product = productService.findById(productId);
        LitemallCart existCart = cartService.queryExist(goodsId, productId, userId);
        if (existCart == null) {
            if (product == null || number > product.getNumber()) {
                return CartAddResult.error(ResponseUtil.fail(GOODS_NO_STOCK, "库存不足"));
            }
            addNewCart(userId, cart, goods, product);
            return CartAddResult.ok(cart.getId());
        }

        int num = merge ? existCart.getNumber() + number : number;
        if (product == null || num > product.getNumber()) {
            return CartAddResult.error(ResponseUtil.fail(GOODS_NO_STOCK, "库存不足"));
        }
        existCart.setNumber((short) num);
        if (cartService.updateById(existCart) == 0) {
            return CartAddResult.error(ResponseUtil.updatedDataFailed());
        }
        return CartAddResult.ok(existCart.getId());
    }

    private void addNewCart(Integer userId, LitemallCart cart, LitemallGoods goods, LitemallGoodsProduct product) {
        cart.setId(null);
        cart.setGoodsSn(goods.getGoodsSn());
        cart.setGoodsName(goods.getName());
        cart.setPicUrl(StringUtils.isEmpty(product.getUrl()) ? goods.getPicUrl() : product.getUrl());
        cart.setPrice(product.getPrice());
        cart.setSpecifications(product.getSpecifications());
        cart.setUserId(userId);
        cart.setChecked(true);
        cartService.add(cart);
    }

    private static class CartAddResult {
        private final Object error;
        private final Integer cartId;

        private CartAddResult(Object error, Integer cartId) {
            this.error = error;
            this.cartId = cartId;
        }

        static CartAddResult error(Object error) {
            return new CartAddResult(error, null);
        }

        static CartAddResult ok(Integer cartId) {
            return new CartAddResult(null, cartId);
        }
    }

    /**
     * 修改购物车商品货品数量
     *
     * @param userId 用户ID
     * @param cart   购物车商品信息， { id: xxx, goodsId: xxx, productId: xxx, number: xxx }
     * @return 修改结果
     */
    @PostMapping("update")
    public Object update(@LoginUser Integer userId, @RequestBody LitemallCart cart) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }
        Integer productId = cart.getProductId();
        Integer number = cart.getNumber().intValue();
        Integer goodsId = cart.getGoodsId();
        Integer id = cart.getId();
        if (!ObjectUtils.allNotNull(id, productId, number, goodsId)) {
            return ResponseUtil.badArgument();
        }
        if(number <= 0){
            return ResponseUtil.badArgument();
        }

        //判断是否存在该订单
        // 如果不存在，直接返回错误
        LitemallCart existCart = cartService.findById(userId, id);
        if (existCart == null) {
            return ResponseUtil.badArgumentValue();
        }

        // 判断goodsId和productId是否与当前cart里的值一致
        if (!existCart.getGoodsId().equals(goodsId)) {
            return ResponseUtil.badArgumentValue();
        }
        if (!existCart.getProductId().equals(productId)) {
            return ResponseUtil.badArgumentValue();
        }

        //判断商品是否可以购买
        LitemallGoods goods = goodsService.findById(goodsId);
        if (goods == null || !goods.getIsOnSale()) {
            return ResponseUtil.fail(GOODS_UNSHELVE, "商品已下架");
        }

        //取得规格的信息,判断规格库存
        LitemallGoodsProduct product = productService.findById(productId);
        if (product == null || product.getNumber() < number) {
            return ResponseUtil.fail(GOODS_UNSHELVE, "库存不足");
        }

        existCart.setNumber(number.shortValue());
        if (cartService.updateById(existCart) == 0) {
            return ResponseUtil.updatedDataFailed();
        }
        return ResponseUtil.ok();
    }

    /**
     * 购物车商品货品勾选状态
     * <p>
     * 如果原来没有勾选，则设置勾选状态；如果商品已经勾选，则设置非勾选状态。
     *
     * @param userId 用户ID
     * @param body   购物车商品信息， { productIds: xxx, isChecked: 1/0 }
     * @return 购物车信息
     */
    @PostMapping("checked")
    public Object checked(@LoginUser Integer userId, @RequestBody String body) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }
        if (body == null) {
            return ResponseUtil.badArgument();
        }

        List<Integer> productIds = JacksonUtil.parseIntegerList(body, "productIds");
        if (productIds == null) {
            return ResponseUtil.badArgument();
        }

        Integer checkValue = JacksonUtil.parseInteger(body, "isChecked");
        if (checkValue == null) {
            return ResponseUtil.badArgument();
        }
        Boolean isChecked = (checkValue == 1);

        cartService.updateCheck(userId, productIds, isChecked);
        return index(userId);
    }

    /**
     * 购物车商品删除
     *
     * @param userId 用户ID
     * @param body   购物车商品信息， { productIds: xxx }
     * @return 购物车信息
     * 成功则
     * {
     * errno: 0,
     * errmsg: '成功',
     * data: xxx
     * }
     * 失败则 { errno: XXX, errmsg: XXX }
     */
    @PostMapping("delete")
    public Object delete(@LoginUser Integer userId, @RequestBody String body) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }
        if (body == null) {
            return ResponseUtil.badArgument();
        }

        List<Integer> productIds = JacksonUtil.parseIntegerList(body, "productIds");

        if (productIds == null || productIds.size() == 0) {
            return ResponseUtil.badArgument();
        }

        cartService.delete(productIds, userId);
        return index(userId);
    }

    /**
     * 购物车商品货品数量
     * <p>
     * 如果用户没有登录，则返回空数据。
     *
     * @param userId 用户ID
     * @return 购物车商品货品数量
     */
    @GetMapping("goodscount")
    public Object goodscount(@LoginUser Integer userId) {
        if (userId == null) {
            return ResponseUtil.ok(0);
        }

        int goodsCount = 0;
        List<LitemallCart> cartList = cartService.queryByUid(userId);
        for (LitemallCart cart : cartList) {
            goodsCount += cart.getNumber();
        }

        return ResponseUtil.ok(goodsCount);
    }

    /**
     * 购物车下单
     *
     * @param userId    用户ID
     * @param cartId    购物车商品ID：
     *                  如果购物车商品ID是空，则下单当前用户所有购物车商品；
     *                  如果购物车商品ID非空，则只下单当前购物车商品。
     * @param addressId 收货地址ID：
     *                  如果收货地址ID是空，则查询当前用户的默认地址。
     * @param couponId  优惠券ID：
     *                  如果优惠券ID是空，则自动选择合适的优惠券。
     * @return 购物车操作结果
     */
    @GetMapping("checkout")
    public Object checkout(@LoginUser Integer userId, Integer cartId, Integer addressId, Integer couponId, Integer userCouponId, Integer grouponRulesId) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }

        AddressSelection address = resolveAddress(userId, addressId);
        LitemallGrouponRules grouponRules = grouponRulesService.findById(grouponRulesId);
        BigDecimal grouponPrice = grouponRules == null ? BigDecimal.ZERO : grouponRules.getDiscount();

        List<LitemallCart> checkedGoodsList = loadCheckedGoods(userId, cartId);
        if (checkedGoodsList == null) {
            return ResponseUtil.badArgumentValue();
        }
        BigDecimal checkedGoodsPrice = calculateGoodsPrice(checkedGoodsList, grouponRules, grouponPrice);
        CouponSelection coupon = selectCoupon(userId, couponId, userCouponId, checkedGoodsPrice, checkedGoodsList);
        BigDecimal freightPrice = calculateFreightPrice(checkedGoodsPrice);

        return ResponseUtil.ok(buildCheckoutData(address, cartId, grouponRulesId, grouponPrice,
                coupon, checkedGoodsPrice, freightPrice, checkedGoodsList));
    }

    private AddressSelection resolveAddress(Integer userId, Integer addressId) {
        LitemallAddress address = null;
        if (addressId != null && !addressId.equals(0)) {
            address = addressService.query(userId, addressId);
        }
        if (address != null) {
            return new AddressSelection(addressId, address);
        }
        address = addressService.findDefault(userId);
        if (address == null) {
            LitemallAddress empty = new LitemallAddress();
            empty.setId(0);
            return new AddressSelection(0, empty);
        }
        return new AddressSelection(address.getId(), address);
    }

    private List<LitemallCart> loadCheckedGoods(Integer userId, Integer cartId) {
        if (cartId == null || cartId.equals(0)) {
            return cartService.queryByUidAndChecked(userId);
        }
        LitemallCart cart = cartService.findById(userId, cartId);
        if (cart == null) {
            return null;
        }
        List<LitemallCart> goodsList = new ArrayList<>(1);
        goodsList.add(cart);
        return goodsList;
    }

    private BigDecimal calculateGoodsPrice(List<LitemallCart> goodsList, LitemallGrouponRules grouponRules, BigDecimal grouponPrice) {
        BigDecimal total = BigDecimal.ZERO;
        for (LitemallCart cart : goodsList) {
            BigDecimal price = cart.getPrice();
            if (grouponRules != null && grouponRules.getGoodsId().equals(cart.getGoodsId())) {
                price = price.subtract(grouponPrice);
            }
            total = total.add(price.multiply(new BigDecimal(cart.getNumber())));
        }
        return total;
    }

    private CouponSelection selectCoupon(Integer userId, Integer couponId, Integer userCouponId,
                                         BigDecimal goodsPrice, List<LitemallCart> goodsList) {
        CouponSelection best = new CouponSelection();
        for (LitemallCouponUser couponUser : couponUserService.queryAll(userId)) {
            LitemallCoupon coupon = couponVerifyService.checkCoupon(userId, couponUser.getCouponId(),
                    couponUser.getId(), goodsPrice, goodsList);
            if (coupon == null) {
                continue;
            }
            best.availableCount++;
            if (coupon.getDiscount().compareTo(best.discount) > 0) {
                best.discount = coupon.getDiscount();
                best.couponId = coupon.getId();
                best.userCouponId = couponUser.getId();
            }
        }
        if (couponId == null || couponId.equals(-1)) {
            return new CouponSelection(-1, -1, BigDecimal.ZERO, best.availableCount);
        }
        if (couponId.equals(0)) {
            return best;
        }
        LitemallCoupon selected = couponVerifyService.checkCoupon(userId, couponId, userCouponId,
                goodsPrice, goodsList);
        if (selected == null) {
            return best;
        }
        return new CouponSelection(couponId, userCouponId, selected.getDiscount(), best.availableCount);
    }

    private BigDecimal calculateFreightPrice(BigDecimal goodsPrice) {
        if (goodsPrice.compareTo(SystemConfig.getFreightLimit()) < 0) {
            return SystemConfig.getFreight();
        }
        return BigDecimal.ZERO;
    }

    private Map<String, Object> buildCheckoutData(AddressSelection address, Integer cartId,
                                                  Integer grouponRulesId, BigDecimal grouponPrice,
                                                  CouponSelection coupon, BigDecimal goodsPrice,
                                                  BigDecimal freightPrice, List<LitemallCart> goodsList) {
        BigDecimal orderTotalPrice = goodsPrice.add(freightPrice).subtract(coupon.discount)
                .max(BigDecimal.ZERO);
        Map<String, Object> data = new HashMap<>();
        data.put("addressId", address.id);
        data.put("couponId", coupon.couponId);
        data.put("userCouponId", coupon.userCouponId);
        data.put("cartId", cartId);
        data.put("grouponRulesId", grouponRulesId);
        data.put("grouponPrice", grouponPrice);
        data.put("checkedAddress", address.address);
        data.put("availableCouponLength", coupon.availableCount);
        data.put("goodsTotalPrice", goodsPrice);
        data.put("freightPrice", freightPrice);
        data.put("couponPrice", coupon.discount);
        data.put("orderTotalPrice", orderTotalPrice);
        data.put("actualPrice", orderTotalPrice);
        data.put("checkedGoodsList", goodsList);
        return data;
    }

    private static class AddressSelection {
        private final Integer id;
        private final LitemallAddress address;

        private AddressSelection(Integer id, LitemallAddress address) {
            this.id = id;
            this.address = address;
        }
    }

    private static class CouponSelection {
        private Integer couponId = 0;
        private Integer userCouponId = 0;
        private BigDecimal discount = BigDecimal.ZERO;
        private int availableCount;

        private CouponSelection() {
        }

        private CouponSelection(Integer couponId, Integer userCouponId, BigDecimal discount, int availableCount) {
            this.couponId = couponId;
            this.userCouponId = userCouponId;
            this.discount = discount;
            this.availableCount = availableCount;
        }
    }

}
