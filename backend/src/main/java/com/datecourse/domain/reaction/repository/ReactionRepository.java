package com.datecourse.domain.reaction.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.datecourse.domain.reaction.entity.Reaction;

public interface ReactionRepository extends JpaRepository<Reaction, Long> {
}
