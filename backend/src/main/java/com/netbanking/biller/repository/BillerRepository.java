package com.netbanking.biller.repository;
import com.netbanking.biller.domain.Biller;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
public interface BillerRepository extends JpaRepository<Biller, Long> {
    @Query("select biller from Biller biller where biller.isActive = 'Y' order by biller.billerName")
    List<Biller> findAllActive();
}
