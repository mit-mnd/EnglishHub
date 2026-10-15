/**
 * Quyết định thông báo nhắc học (hàm thuần, chuyển từ ReminderPolicy bản Java):
 *  - STUDY_REMINDER: đã qua giờ nhắc người học chọn mà chưa đạt mục tiêu ngày (tối đa 1 lần/ngày).
 *  - STREAK: sau 20:00, chuỗi >= 2 ngày mà hôm nay chưa học (tối đa 1 lần/ngày).
 *
 * @param c { localTime 'HH:mm', reminderEnabled, reminderTime 'HH:mm'|null, studyReminderSentToday,
 *            streakWarningSentToday, studyMinutesToday, dailyGoalMinutes, currentStreak, studiedToday, dueCards }
 * @returns {{ type, title, message, link }[]}
 */
export const STREAK_WARNING_FROM = '20:00'

export function decideReminders(c) {
  const result = []
  if (!c.reminderEnabled) return result
  if (c.reminderTime && c.localTime >= c.reminderTime && !c.studyReminderSentToday
      && c.studyMinutesToday < c.dailyGoalMinutes) {
    const remaining = c.dailyGoalMinutes - c.studyMinutesToday
    result.push(c.dueCards > 0
      ? { type: 'STUDY_REMINDER', title: 'Đến giờ học rồi! ⏰',
          message: `Bạn có ${c.dueCards} thẻ từ vựng cần ôn và còn ${remaining} phút để đạt mục tiêu hôm nay.`,
          link: '/app/vocabulary/review' }
      : { type: 'STUDY_REMINDER', title: 'Đến giờ học rồi! ⏰',
          message: `Còn ${remaining} phút nữa là đạt mục tiêu hôm nay. Học tiếp lộ trình nhé!`, link: '/app/path' })
  }
  if (c.currentStreak >= 2 && !c.studiedToday && c.localTime >= STREAK_WARNING_FROM && !c.streakWarningSentToday) {
    result.push({
      type: 'STREAK', title: `Đừng để mất chuỗi ${c.currentStreak} ngày! 🔥`,
      message: 'Hôm nay bạn chưa học. Chỉ cần ôn vài thẻ từ vựng để giữ chuỗi ngày học.',
      link: c.dueCards > 0 ? '/app/vocabulary/review' : '/app/vocabulary',
    })
  }
  return result
}
