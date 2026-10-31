-- 订单列表按用户和下单时间查询，原表只有主键，压测时全表扫描
ALTER TABLE litemall_order ADD INDEX idx_user_add_time (user_id, add_time);
