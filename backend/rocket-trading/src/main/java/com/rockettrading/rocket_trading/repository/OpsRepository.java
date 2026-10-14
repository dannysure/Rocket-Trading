package com.rockettrading.rocket_trading.repository;

import com.rockettrading.rocket_trading.repository.model.OpsOrderSummaryRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** Cross-client trade lookups for the operations role (persona David, BR-15). */
@Mapper
public interface OpsRepository {

    @Select("""
            <script>
            select o.order_id, o.client_id, o.account_id, o.instrument_id, o.market,
                   fi.ticker_symbol as symbol, fi.instrument_name, fi.base_currency as instrument_currency,
                   o.order_side, o.order_type, o.requested_quantity, o.limit_price,
                   o.order_status, o.rejection_reason, o.submitted_at, o.next_execution_at,
                   f.executed_price,
                   coalesce(f.settlement_amount, f.executed_quantity * f.executed_price) as settlement_amount,
                   coalesce(f.settlement_currency, ca.currency) as settlement_currency,
                   f.executed_at,
                   cp.client_full_name as client_name, cp.email_address as client_email
            from orders o
            join financial_instruments fi on fi.instrument_id = o.instrument_id
            join client_accounts ca on ca.account_id = o.account_id
            join client_profiles cp on cp.client_id = o.client_id
            left join fills f on f.order_id = o.order_id
            <where>
              <if test="orderId != null">and o.order_id = #{orderId}</if>
              <if test="email != null">and lower(cp.email_address) like '%' || lower(#{email}) || '%'</if>
              <if test="symbol != null">and upper(fi.ticker_symbol) = upper(#{symbol})</if>
              <if test="status != null">and o.order_status = #{status}</if>
            </where>
            order by o.submitted_at desc, o.order_id desc
            limit #{limit}
            </script>
            """)
    List<OpsOrderSummaryRecord> search(@Param("orderId") Long orderId,
                                       @Param("email") String email,
                                       @Param("symbol") String symbol,
                                       @Param("status") String status,
                                       @Param("limit") int limit);

    @Insert("insert into staff_access_log (staff_client_id, resource) values (#{staffId}, #{resource})")
    int logAccess(@Param("staffId") long staffId, @Param("resource") String resource);
}
