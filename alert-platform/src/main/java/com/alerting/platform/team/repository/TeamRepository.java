package com.alerting.platform.team.repository;

import com.alerting.platform.team.model.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeamRepository extends JpaRepository<Team, Long> {

    List<Team> findByActiveTrue();

    Optional<Team> findByNameAndActiveTrue(String name);

    @Query("SELECT t FROM Team t JOIN t.responsibleFeatures f WHERE f = :feature AND t.active = true")
    Optional<Team> findByResponsibleFeaturesContainingAndActiveTrue(@Param("feature") String feature);

    @Query("SELECT t FROM Team t JOIN t.responsibleApps a WHERE a = :appId AND t.active = true")
    Optional<Team> findByResponsibleAppsContainingAndActiveTrue(@Param("appId") String appId);

    @Query("SELECT DISTINCT t FROM Team t LEFT JOIN FETCH t.members WHERE t.active = true")
    List<Team> findAllActiveWithMembers();
}