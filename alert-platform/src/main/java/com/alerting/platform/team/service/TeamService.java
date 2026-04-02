package com.alerting.platform.team.service;

import com.alerting.platform.team.model.Team;
import com.alerting.platform.team.model.TeamMember;
import com.alerting.platform.team.model.TeamRole;
import com.alerting.platform.team.repository.TeamMemberRepository;
import com.alerting.platform.team.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class TeamService {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository memberRepository;

    // ========== Team Lookup ==========

    @Cacheable(value = "responsibleTeam", key = "#appId + ':' + #feature")
    public Team findResponsibleTeam(String appId, String feature) {
        log.debug("Finding responsible team for app={}, feature={}", appId, feature);
        
        // First try to find by specific feature
        Optional<Team> teamByFeature = teamRepository.findByResponsibleFeaturesContainingAndActiveTrue(feature);
        if (teamByFeature.isPresent()) {
            log.debug("Found team {} by feature", teamByFeature.get().getName());
            return teamByFeature.get();
        }

        // Fall back to app-level responsibility
        Optional<Team> teamByApp = teamRepository.findByResponsibleAppsContainingAndActiveTrue(appId);
        if (teamByApp.isPresent()) {
            log.debug("Found team {} by app", teamByApp.get().getName());
            return teamByApp.get();
        }

        log.warn("No responsible team found for app={}, feature={}", appId, feature);
        return null;
    }

    // ========== On-Call Management ==========

    public TeamMember getOnCallMember(Team team) {
        return memberRepository.findByTeamAndOnCallTrueAndActiveTrue(team)
            .stream()
            .findFirst()
            .orElse(null);
    }

    @Transactional
    public TeamMember setOnCall(Long memberId, boolean onCall) {
        TeamMember member = getMemberById(memberId);
        
        if (onCall) {
            // Clear existing on-call for this team
            memberRepository.clearOnCallForTeam(member.getTeam().getId());
        }
        
        member.setOnCall(onCall);
        return memberRepository.save(member);
    }

    // ========== Team Member Queries ==========

    public List<TeamMember> getTeamLeads(Team team) {
        return memberRepository.findByTeamAndRoleInAndActiveTrue(
            team, 
            List.of(TeamRole.LEAD, TeamRole.MANAGER, TeamRole.ADMIN)
        );
    }

    public List<TeamMember> getMembersByRole(Team team, List<TeamRole> roles) {
        return memberRepository.findByTeamAndRoleInAndActiveTrue(team, roles);
    }

    public List<TeamMember> getActiveTeamMembers(Team team) {
        return memberRepository.findByTeamAndActiveTrue(team);
    }

    public TeamMember getMemberById(Long memberId) {
        return memberRepository.findById(memberId)
            .orElseThrow(() -> new RuntimeException("Team member not found: " + memberId));
    }

    public Optional<TeamMember> findMemberByEmail(String email) {
        return memberRepository.findByEmailAndActiveTrue(email);
    }

    // ========== Team CRUD ==========

    @Transactional
    @CacheEvict(value = "responsibleTeam", allEntries = true)
    public Team createTeam(Team team) {
        log.info("Creating team: {}", team.getName());
        return teamRepository.save(team);
    }

    @Transactional
    @CacheEvict(value = "responsibleTeam", allEntries = true)
    public Team updateTeam(Long teamId, Team updates) {
        Team existing = teamRepository.findById(teamId)
            .orElseThrow(() -> new RuntimeException("Team not found: " + teamId));

        existing.setName(updates.getName());
        existing.setDescription(updates.getDescription());
        existing.setSlackChannel(updates.getSlackChannel());
        existing.setEmailDistribution(updates.getEmailDistribution());
        existing.setResponsibleFeatures(updates.getResponsibleFeatures());
        existing.setResponsibleApps(updates.getResponsibleApps());

        return teamRepository.save(existing);
    }

    @Transactional
    @CacheEvict(value = "responsibleTeam", allEntries = true)
    public void assignFeatureToTeam(Long teamId, String feature) {
        Team team = teamRepository.findById(teamId)
            .orElseThrow(() -> new RuntimeException("Team not found: " + teamId));
        
        team.getResponsibleFeatures().add(feature);
        teamRepository.save(team);
        log.info("Assigned feature {} to team {}", feature, team.getName());
    }

    @Transactional
    @CacheEvict(value = "responsibleTeam", allEntries = true)
    public void assignAppToTeam(Long teamId, String appId) {
        Team team = teamRepository.findById(teamId)
            .orElseThrow(() -> new RuntimeException("Team not found: " + teamId));
        
        team.getResponsibleApps().add(appId);
        teamRepository.save(team);
        log.info("Assigned app {} to team {}", appId, team.getName());
    }

    // ========== Team Member CRUD ==========

    @Transactional
    public TeamMember addMember(Long teamId, TeamMember member) {
        Team team = teamRepository.findById(teamId)
            .orElseThrow(() -> new RuntimeException("Team not found: " + teamId));

        member.setTeam(team);
        member.setActive(true);
        
        TeamMember saved = memberRepository.save(member);
        log.info("Added member {} to team {}", member.getName(), team.getName());
        
        return saved;
    }

    @Transactional
    public TeamMember updateMember(Long memberId, TeamMember updates) {
        TeamMember existing = getMemberById(memberId);

        existing.setName(updates.getName());
        existing.setEmail(updates.getEmail());
        existing.setSlackUserId(updates.getSlackUserId());
        existing.setPhoneNumber(updates.getPhoneNumber());
        existing.setRole(updates.getRole());

        return memberRepository.save(existing);
    }

    @Transactional
    public void deactivateMember(Long memberId) {
        TeamMember member = getMemberById(memberId);
        member.setActive(false);
        member.setOnCall(false);
        memberRepository.save(member);
        log.info("Deactivated member: {}", member.getName());
    }

    // ========== Queries ==========

    public List<Team> getAllActiveTeams() {
        return teamRepository.findByActiveTrue();
    }

    public Team getTeamById(Long teamId) {
        return teamRepository.findById(teamId)
            .orElseThrow(() -> new RuntimeException("Team not found: " + teamId));
    }

    public TeamWorkload getTeamWorkload(Long teamId) {
        Team team = getTeamById(teamId);
        List<TeamMember> members = getActiveTeamMembers(team);
        
        // This would be calculated from AlertRepository
        // Placeholder for now
        return TeamWorkload.builder()
            .team(team)
            .totalMembers(members.size())
            .onCallMember(getOnCallMember(team))
            .build();
    }

    @lombok.Builder
    @lombok.Data
    public static class TeamWorkload {
        private Team team;
        private int totalMembers;
        private int activeAlerts;
        private int unacknowledgedAlerts;
        private TeamMember onCallMember;
    }
}