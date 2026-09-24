package com.finsight_backend.repository;

import com.finsight_backend.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.finsight_backend.enums.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findAllByUserId(Long userId);
    Optional<Transaction> findByIdAndUserId(Long id, Long userId);

    interface TypeTotal {
        TransactionType getType();
        BigDecimal getTotal();
    }

    interface MonthTotal extends TypeTotal {
        Integer getReportYear();
        Integer getReportMonth();
    }

    interface CategoryTotal {
        Long getCategoryId();
        String getCategoryName();
        TransactionType getCategoryType();
        BigDecimal getTotal();
    }

    @Query("select t.type as type, sum(t.amount) as total from Transaction t "
            + "where t.user.id = :userId group by t.type")
    List<TypeTotal> sumByType(@Param("userId") Long userId);

    @Query("select year(t.date) as reportYear, month(t.date) as reportMonth, t.type as type, "
            + "sum(t.amount) as total from Transaction t where t.user.id = :userId "
            + "and t.date >= :start and t.date < :end "
            + "group by year(t.date), month(t.date), t.type")
    List<MonthTotal> sumByMonthAndType(@Param("userId") Long userId,
                                     @Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("select c.id as categoryId, c.name as categoryName, c.type as categoryType, "
            + "sum(t.amount) as total from Transaction t join t.category c "
            + "where t.user.id = :userId and t.type = :type and t.date >= :start and t.date < :end "
            + "group by c.id, c.name, c.type order by sum(t.amount) desc, c.id asc")
    List<CategoryTotal> sumByCategory(@Param("userId") Long userId, @Param("type") TransactionType type,
                                     @Param("start") LocalDate start, @Param("end") LocalDate end);

    @EntityGraph(attributePaths = "category")
    List<Transaction> findTop5ByUserIdOrderByDateDescIdDesc(Long userId);
}
