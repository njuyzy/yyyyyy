package com.example.Japp.user.util;

import com.example.Japp.network.models.Project;
import org.junit.Test;
import static org.junit.Assert.*;

public class UserOrderPresentationTest {
    @Test public void groupedStatesNeverLeakBackendNames() {
        assertEquals("招募中", UserOrderPresentation.statusLabel("MATCHING"));
        assertEquals("招募中", UserOrderPresentation.statusLabel(" open "));
        assertEquals("已确认", UserOrderPresentation.statusLabel("IN_PROGRESS"));
        assertEquals("已确认", UserOrderPresentation.statusLabel("CONFIRMED"));
        assertEquals("已完成", UserOrderPresentation.statusLabel("DONE"));
        assertEquals("已取消", UserOrderPresentation.statusLabel("CANCELLED"));
        assertEquals("", UserOrderPresentation.statusLabel("INTERNAL_RECONCILING"));
        assertEquals("", UserOrderPresentation.statusLabel(null));
    }

    @Test public void cancellationIsNotCompletionOrRecruitment() {
        Project project = new Project();
        project.setStatus("CANCELLED");
        project.setMaxMembers(12);
        project.setCurrentMembers(3);
        assertEquals("CANCELLED", UserOrderPresentation.phase(project.getStatus()));
        assertEquals("本次行程已取消", UserOrderPresentation.remaining(project));
    }

    @Test public void capacityUsesRepresentedTotalAndSupportsUnlimited() {
        Project project = new Project();
        project.setStatus("OPEN");
        project.setMaxMembers(12);
        project.setCurrentMembers(9);
        assertEquals("9 / 12 人", UserOrderPresentation.capacity(project));
        assertEquals("还可加入 3 人", UserOrderPresentation.remaining(project));
        project.setCurrentMembers(14);
        assertEquals("名额已满", UserOrderPresentation.remaining(project));
        project.setMaxMembers(null);
        assertEquals("14 / 不限人数", UserOrderPresentation.capacity(project));
    }

    @Test public void storageCodesAndNullStringsAreNotCityNames() {
        assertEquals("", UserOrderPresentation.city("320105"));
        assertEquals("", UserOrderPresentation.city("null"));
        assertEquals("南京市", UserOrderPresentation.city("南京市"));
    }

    @Test public void departureDisplaysHumanTimeWithoutDroppingMinutes() {
        Project project = new Project();
        project.setDepartureDate("2026-09-12T09:30:00");
        assertEquals("2026-09-12  09:30", UserOrderPresentation.departure(project));
        project.setDepartureDate("2026-09-12");
        project.setDepartureTime("09:00:00");
        assertEquals("2026-09-12  09:00", UserOrderPresentation.departure(project));
        project.setDepartureDate(null);
        assertEquals("出发时间待确认", UserOrderPresentation.departure(project));
    }
}
