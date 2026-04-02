package com.alerting.platform.team.repository;

import com.alerting.platform.team.model.Team;
import com.alerting.platform.team.model.TeamMember;
import com.alerting.platform.team.model.TeamRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {

    List<TeamMember> findByTeamAndActiveTrue(Team team);

    List<TeamMember> findByTeamAndOnCallTrueAndActiveTrue(Team team);

    List<TeamMember> findByTeamAndRoleInAndActiveTrue(Team team, List<TeamRole> roles);

    Optional<TeamMember> findByEmailAndActiveTrue(String email);

    Optional<TeamMember> findByMemberIdAndActiveTrue(String memberId);

    Optional<TeamMember> findBySlackUserIdAndActiveTrue(String slackUserId);

    @Modifying
    @Query("UPDATE TeamMember m SET m.onCall = false WHERE m.team.id = :teamId")
    void clearOnCallForTeam(@Param("teamId") Long teamId);

    @Query("SELECT m FROM TeamMember m WHERE m.team.id = :teamId AND m.active = true ORDER BY m.role DESC, m.name ASC")
    List<TeamMember> findByTeamIdOrderByRole(@Param("teamId") Long teamId);

    @Query("SELECT COUNT(m) FROM TeamMember m WHERE m.team = :team AND m.active = true")
    int countActiveMembers(@Param("team") Team team);
}