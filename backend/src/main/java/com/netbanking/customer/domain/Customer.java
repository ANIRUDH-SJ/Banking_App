package com.netbanking.customer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "customer")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "customer_number", nullable = false, unique = true)
    private String customerNumber;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Column(name = "mobile_number", nullable = false)
    private String mobileNumber;

    @Column(name = "kyc_status", nullable = false)
    private String kycStatus;

    @Column(name = "is_active", nullable = false, columnDefinition = "CHAR(1)")
    private String isActive;

    protected Customer() {
    }

    public Customer(Long customerId, Long userId, String customerNumber, String firstName,
                    String lastName, LocalDate dateOfBirth, String mobileNumber,
                    String kycStatus, String isActive) {
        this.customerId = customerId;
        this.userId = userId;
        this.customerNumber = customerNumber;
        this.firstName = firstName;
        this.lastName = lastName;
        this.dateOfBirth = dateOfBirth;
        this.mobileNumber = mobileNumber;
        this.kycStatus = kycStatus;
        this.isActive = isActive;
    }

    public Long getCustomerId() { return customerId; }
    public Long getUserId() { return userId; }
    public String getCustomerNumber() { return customerNumber; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getMobileNumber() { return mobileNumber; }
    public String getKycStatus() { return kycStatus; }
    public String getIsActive() { return isActive; }
}
