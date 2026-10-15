package com.englishhub.modules.catalog.entity;

import com.englishhub.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "Levels")
public class Level extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 30)
    private String code;

    @Nationalized
    @Column(nullable = false, length = 100)
    private String name;

    @Nationalized
    @Column(length = 500)
    private String description;

    @Column(length = 20)
    private String cefrRange;

    /** % điểm tối thiểu của Placement Test để được xếp vào level này. */
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal placementMinPercent;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal placementMaxPercent;

    @Column(nullable = false)
    private Integer sortOrder;
}
