package com.netbanking.biller.provider;

public interface BillerPaymentAdapter {
    BillerPaymentReceipt collect(BillerPaymentCommand command);
}
