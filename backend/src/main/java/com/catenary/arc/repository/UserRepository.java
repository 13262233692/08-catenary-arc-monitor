package com.catenary.arc.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import com.catenary.arc.entity.RoleType;
import com.catenary.arc.entity.User;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
public class UserRepository {

    private final Map<Long, User> users = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @PostConstruct
    public void init() {
        save(new User(null, "admin", "admin123", "系统管理员", RoleType.ADMIN, null, null, null, true));
        save(new User(null, "bureau1", "bureau123", "路局用户1", RoleType.BUREAU, "B001", null, null, true));
        save(new User(null, "station1", "station123", "站点用户1", RoleType.STATION, "B001", "S001", null, true));
        save(new User(null, "area1", "area123", "工区用户1", RoleType.WORK_AREA, "B001", "S001", "W001", true));
        log.info("Initialized {} demo users", users.size());
    }

    public Optional<User> findByUsername(String username) {
        return users.values().stream()
                .filter(user -> user.getUsername().equals(username))
                .findFirst();
    }

    public List<User> findByBureauId(String bureauId) {
        return users.values().stream()
                .filter(user -> bureauId.equals(user.getBureauId()))
                .collect(Collectors.toList());
    }

    public List<User> findByStationId(String stationId) {
        return users.values().stream()
                .filter(user -> stationId.equals(user.getStationId()))
                .collect(Collectors.toList());
    }

    public List<User> findByWorkAreaId(String workAreaId) {
        return users.values().stream()
                .filter(user -> workAreaId.equals(user.getWorkAreaId()))
                .collect(Collectors.toList());
    }

    private void save(User user) {
        user.setId(idGenerator.getAndIncrement());
        users.put(user.getId(), user);
    }
}
