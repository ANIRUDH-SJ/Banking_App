package com.netbanking.ledger.external;

public interface ExternalTransferAdapter {
    ExternalTransferReceipt transfer(ExternalTransferCommand command);
}
