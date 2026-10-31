package org.linlinjava.litemall.admin.service;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.linlinjava.litemall.admin.dto.GoodsAllinone;
import org.linlinjava.litemall.admin.vo.CatVo;
import org.linlinjava.litemall.core.qcode.QCodeService;
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.db.domain.*;
import org.linlinjava.litemall.db.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.linlinjava.litemall.admin.util.AdminResponseCode.GOODS_NAME_EXIST;

@Service
public class AdminGoodsService {
    private final Log logger = LogFactory.getLog(AdminGoodsService.class);

    @Autowired
    private LitemallGoodsService goodsService;
    @Autowired
    private LitemallGoodsSpecificationService specificationService;
    @Autowired
    private LitemallGoodsAttributeService attributeService;
    @Autowired
    private LitemallGoodsProductService productService;
    @Autowired
    private LitemallCategoryService categoryService;
    @Autowired
    private LitemallBrandService brandService;
    @Autowired
    private LitemallCartService cartService;
    @Autowired
    private QCodeService qCodeService;

    public Object list(Integer goodsId, String goodsSn, String name,
                       Integer page, Integer limit, String sort, String order) {
        List<LitemallGoods> goodsList = goodsService.querySelective(goodsId, goodsSn, name, page, limit, sort, order);
        return ResponseUtil.okList(goodsList);
    }

    private Object validate(GoodsAllinone goodsAllinone) {
        Object error = validateGoods(goodsAllinone.getGoods());
        if (error != null) {
            return error;
        }
        error = validateAttributes(goodsAllinone.getAttributes());
        if (error != null) {
            return error;
        }
        error = validateSpecifications(goodsAllinone.getSpecifications());
        if (error != null) {
            return error;
        }
        return validateProducts(goodsAllinone.getProducts());
    }

    private Object validateGoods(LitemallGoods goods) {
        if (StringUtils.isEmpty(goods.getName()) || StringUtils.isEmpty(goods.getGoodsSn())) {
            return ResponseUtil.badArgument();
        }
        Integer brandId = goods.getBrandId();
        if (brandId != null && brandId != 0 && brandService.findById(brandId) == null) {
            return ResponseUtil.badArgumentValue();
        }
        Integer categoryId = goods.getCategoryId();
        if (categoryId != null && categoryId != 0 && categoryService.findById(categoryId) == null) {
            return ResponseUtil.badArgumentValue();
        }
        return null;
    }

    private Object validateAttributes(LitemallGoodsAttribute[] attributes) {
        for (LitemallGoodsAttribute attribute : attributes) {
            if (StringUtils.isEmpty(attribute.getAttribute()) || StringUtils.isEmpty(attribute.getValue())) {
                return ResponseUtil.badArgument();
            }
        }
        return null;
    }

    private Object validateSpecifications(LitemallGoodsSpecification[] specifications) {
        for (LitemallGoodsSpecification specification : specifications) {
            if (StringUtils.isEmpty(specification.getSpecification())
                    || StringUtils.isEmpty(specification.getValue())) {
                return ResponseUtil.badArgument();
            }
        }
        return null;
    }

    private Object validateProducts(LitemallGoodsProduct[] products) {
        for (LitemallGoodsProduct product : products) {
            Integer number = product.getNumber();
            if (number == null || number < 0) {
                return ResponseUtil.badArgument();
            }
            if (product.getPrice() == null || product.getSpecifications().length == 0) {
                return ResponseUtil.badArgument();
            }
        }
        return null;
    }

    /**
     * 编辑商品
     *
     * NOTE：
     * 由于商品涉及到四个表，特别是litemall_goods_product表依赖litemall_goods_specification表，
     * 这导致允许所有字段都是可编辑会带来一些问题，因此这里商品编辑功能是受限制：
     * （1）litemall_goods表可以编辑字段；
     * （2）litemall_goods_specification表只能编辑pic_url字段，其他操作不支持；
     * （3）litemall_goods_product表只能编辑price, number和url字段，其他操作不支持；
     * （4）litemall_goods_attribute表支持编辑、添加和删除操作。
     *
     * NOTE2:
     * 前后端这里使用了一个小技巧：
     * 如果前端传来的update_time字段是空，则说明前端已经更新了某个记录，则这个记录会更新；
     * 否则说明这个记录没有编辑过，无需更新该记录。
     *
     * NOTE3:
     * （1）购物车缓存了一些商品信息，因此需要及时更新。
     * 目前这些字段是goods_sn, goods_name, price, pic_url。
     * （2）但是订单里面的商品信息则是不会更新。
     * 如果订单是未支付订单，此时仍然以旧的价格支付。
     */
    @Transactional
    public Object update(GoodsAllinone goodsAllinone) {
        Object error = validate(goodsAllinone);
        if (error != null) {
            return error;
        }

        LitemallGoods goods = goodsAllinone.getGoods();
        LitemallGoodsSpecification[] specifications = goodsAllinone.getSpecifications();
        LitemallGoodsProduct[] products = goodsAllinone.getProducts();
        LitemallGoodsAttribute[] attributes = goodsAllinone.getAttributes();

        String url = qCodeService.createGoodShareImage(goods.getId().toString(), goods.getPicUrl(), goods.getName());
        goods.setShareUrl(url);
        goods.setRetailPrice(calculateRetailPrice(products));

        if (goodsService.updateById(goods) == 0) {
            throw new RuntimeException("更新数据失败");
        }

        updateSpecifications(specifications);
        updateProducts(products);
        updateAttributes(goods.getId(), attributes);
        updateCartProducts(products, goods);
        return ResponseUtil.ok();
    }

    private BigDecimal calculateRetailPrice(LitemallGoodsProduct[] products) {
        BigDecimal retailPrice = new BigDecimal(Integer.MAX_VALUE);
        for (LitemallGoodsProduct product : products) {
            if (retailPrice.compareTo(product.getPrice()) == 1) {
                retailPrice = product.getPrice();
            }
        }
        return retailPrice;
    }

    private void updateSpecifications(LitemallGoodsSpecification[] specifications) {
        for (LitemallGoodsSpecification specification : specifications) {
            if (specification.getUpdateTime() == null) {
                specification.setSpecification(null);
                specification.setValue(null);
                specificationService.updateById(specification);
            }
        }
    }

    private void updateProducts(LitemallGoodsProduct[] products) {
        for (LitemallGoodsProduct product : products) {
            if (product.getUpdateTime() == null) {
                productService.updateById(product);
            }
        }
    }

    private void updateAttributes(Integer goodsId, LitemallGoodsAttribute[] attributes) {
        for (LitemallGoodsAttribute attribute : attributes) {
            if (attribute.getId() == null || attribute.getId().equals(0)) {
                attribute.setGoodsId(goodsId);
                attributeService.add(attribute);
            } else if (attribute.getDeleted()) {
                attributeService.deleteById(attribute.getId());
            } else if (attribute.getUpdateTime() == null) {
                attributeService.updateById(attribute);
            }
        }
    }

    private void updateCartProducts(LitemallGoodsProduct[] products, LitemallGoods goods) {
        for (LitemallGoodsProduct product : products) {
            cartService.updateProduct(product.getId(), goods.getGoodsSn(), goods.getName(),
                    product.getPrice(), product.getUrl());
        }
    }

    @Transactional
    public Object delete(LitemallGoods goods) {
        Integer id = goods.getId();
        if (id == null) {
            return ResponseUtil.badArgument();
        }

        Integer gid = goods.getId();
        goodsService.deleteById(gid);
        specificationService.deleteByGid(gid);
        attributeService.deleteByGid(gid);
        productService.deleteByGid(gid);
        return ResponseUtil.ok();
    }

    @Transactional
    public Object create(GoodsAllinone goodsAllinone) {
        Object error = validate(goodsAllinone);
        if (error != null) {
            return error;
        }

        LitemallGoods goods = goodsAllinone.getGoods();
        if (goodsService.checkExistByName(goods.getName())) {
            return ResponseUtil.fail(GOODS_NAME_EXIST, "商品名已经存在");
        }
        goods.setRetailPrice(calculateRetailPrice(goodsAllinone.getProducts()));
        goodsService.add(goods);

        updateShareUrl(goods);
        addSpecifications(goods.getId(), goodsAllinone.getSpecifications());
        addAttributes(goods.getId(), goodsAllinone.getAttributes());
        addProducts(goods.getId(), goodsAllinone.getProducts());
        return ResponseUtil.ok();
    }

    private void updateShareUrl(LitemallGoods goods) {
        String url = qCodeService.createGoodShareImage(goods.getId().toString(), goods.getPicUrl(), goods.getName());
        if (!StringUtils.isEmpty(url)) {
            goods.setShareUrl(url);
            if (goodsService.updateById(goods) == 0) {
                throw new RuntimeException("更新数据失败");
            }
        }
    }

    private void addSpecifications(Integer goodsId, LitemallGoodsSpecification[] specifications) {
        for (LitemallGoodsSpecification specification : specifications) {
            specification.setGoodsId(goodsId);
            specificationService.add(specification);
        }
    }

    private void addAttributes(Integer goodsId, LitemallGoodsAttribute[] attributes) {
        for (LitemallGoodsAttribute attribute : attributes) {
            attribute.setGoodsId(goodsId);
            attributeService.add(attribute);
        }
    }

    private void addProducts(Integer goodsId, LitemallGoodsProduct[] products) {
        for (LitemallGoodsProduct product : products) {
            product.setGoodsId(goodsId);
            productService.add(product);
        }
    }

    public Object list2() {
        // http://element-cn.eleme.io/#/zh-CN/component/cascader
        // 管理员设置“所属分类”
        List<LitemallCategory> l1CatList = categoryService.queryL1();
        List<CatVo> categoryList = new ArrayList<>(l1CatList.size());

        for (LitemallCategory l1 : l1CatList) {
            CatVo l1CatVo = new CatVo();
            l1CatVo.setValue(l1.getId());
            l1CatVo.setLabel(l1.getName());

            List<LitemallCategory> l2CatList = categoryService.queryByPid(l1.getId());
            List<CatVo> children = new ArrayList<>(l2CatList.size());
            for (LitemallCategory l2 : l2CatList) {
                CatVo l2CatVo = new CatVo();
                l2CatVo.setValue(l2.getId());
                l2CatVo.setLabel(l2.getName());
                children.add(l2CatVo);
            }
            l1CatVo.setChildren(children);

            categoryList.add(l1CatVo);
        }

        // http://element-cn.eleme.io/#/zh-CN/component/select
        // 管理员设置“所属品牌商”
        List<LitemallBrand> list = brandService.all();
        List<Map<String, Object>> brandList = new ArrayList<>(l1CatList.size());
        for (LitemallBrand brand : list) {
            Map<String, Object> b = new HashMap<>(2);
            b.put("value", brand.getId());
            b.put("label", brand.getName());
            brandList.add(b);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("categoryList", categoryList);
        data.put("brandList", brandList);
        return ResponseUtil.ok(data);
    }

    public Object detail(Integer id) {
        LitemallGoods goods = goodsService.findById(id);
        List<LitemallGoodsProduct> products = productService.queryByGid(id);
        List<LitemallGoodsSpecification> specifications = specificationService.queryByGid(id);
        List<LitemallGoodsAttribute> attributes = attributeService.queryByGid(id);

        Integer categoryId = goods.getCategoryId();
        LitemallCategory category = categoryService.findById(categoryId);
        Integer[] categoryIds = new Integer[]{};
        if (category != null) {
            Integer parentCategoryId = category.getPid();
            categoryIds = new Integer[]{parentCategoryId, categoryId};
        }

        Map<String, Object> data = new HashMap<>();
        data.put("goods", goods);
        data.put("specifications", specifications);
        data.put("products", products);
        data.put("attributes", attributes);
        data.put("categoryIds", categoryIds);

        return ResponseUtil.ok(data);
    }

}
