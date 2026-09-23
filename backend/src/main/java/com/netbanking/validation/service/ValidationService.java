package com.netbanking.validation.service;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
@Service public class ValidationService {
 public void requirePositive(BigDecimal amount,String field){if(amount==null||amount.signum()<=0)throw new IllegalArgumentException(field+" must be greater than zero.");}
 public void requireDifferent(Long first,Long second,String message){if(first!=null&&first.equals(second))throw new IllegalArgumentException(message);}
 public void requireNonBlank(String value,String field){if(value==null||value.isBlank())throw new IllegalArgumentException(field+" is required.");}
}
