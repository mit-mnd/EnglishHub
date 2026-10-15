package com.englishhub.modules.user.repository;

import com.englishhub.modules.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    @EntityGraph(attributePaths = {"roles", "currentLevel"})
    Optional<User> findByEmail(String email);

    @EntityGraph(attributePaths = {"roles", "currentLevel"})
    Optional<User> findWithDetailsById(Long id);

    boolean existsByEmail(String email);

    /** Học viên đang hoạt động và bật nhắc học (đối tượng của job nhắc học). */
    @Query("""
            select u.id from User u
            where u.status = com.englishhub.modules.user.entity.UserStatus.ACTIVE and u.reminderEnabled = true""")
    List<Long> findReminderCandidateIds();

    /** Khóa dòng user để tuần tự hóa thao tác (vd: tránh tạo 2 bài placement song song). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findForUpdate(@Param("id") Long id);

    /** Chỉ fetch-join quan hệ ManyToOne; roles được nạp theo lô (default_batch_fetch_size) để phân trang ở DB. */
    @Override
    @EntityGraph(attributePaths = {"currentLevel"})
    Page<User> findAll(Specification<User> spec, Pageable pageable);
}
