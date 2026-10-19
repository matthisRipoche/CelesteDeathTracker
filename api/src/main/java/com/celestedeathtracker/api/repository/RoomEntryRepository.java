package com.celestedeathtracker.api.repository;

import com.celestedeathtracker.api.model.RoomEntry;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomEntryRepository extends JpaRepository<RoomEntry, Long> {

    List<RoomEntry> findByAttemptIdOrderByEnteredAtAsc(Long attemptId);
}
