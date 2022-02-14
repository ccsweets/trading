package io.runon.trading.account;

import java.math.BigDecimal;

/**
 * 종목별 가격 얻기
 * @author macle
 */
public interface SymbolPrice {

    BigDecimal getPrice(String symbol);
}