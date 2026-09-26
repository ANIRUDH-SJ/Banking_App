package com.netbanking.biller.repository;

import com.netbanking.biller.domain.Biller;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface BillerRepository extends JpaRepository<Biller, Long> {
    @Query(
            "select biller from Biller biller where biller.isActive = 'Y' order by"
                    + " biller.billerName")
    List<Biller> findAllActive();
}
