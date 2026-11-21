package com.isaacggr.goldforge.repository;

import com.isaacggr.goldforge.domain.entity.GoalContribution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GoalContributionRepository extends JpaRepository<GoalContribution, UUID> {

    @Query("SELECT gc FROM GoalContribution gc WHERE gc.goal.id = :goalId ORDER BY gc.contributionDate DESC, gc.createdAt DESC")
    List<GoalContribution> findByGoalIdOrderByContributionDateDescCreatedAtDesc(@Param("goalId") UUID goalId);

    @Query("SELECT gc FROM GoalContribution gc WHERE gc.id = :id AND gc.goal.id = :goalId AND gc.goal.user.id = :userId")
    Optional<GoalContribution> findByIdAndGoalIdAndGoalUserId(@Param("id") UUID id, @Param("goalId") UUID goalId, @Param("userId") UUID userId);

    @Query("SELECT gc FROM GoalContribution gc WHERE gc.transaction.id = :transactionId")
    Optional<GoalContribution> findByTransactionId(@Param("transactionId") UUID transactionId);
}
