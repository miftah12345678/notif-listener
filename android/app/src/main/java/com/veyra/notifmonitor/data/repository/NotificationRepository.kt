package com.veyra.notifmonitor.data.repository

import com.veyra.notifmonitor.data.local.NotificationDao
import com.veyra.notifmonitor.data.local.NotificationEvent
import kotlinx.coroutines.flow.Flow

class NotificationRepository(
    private val notificationDao: NotificationDao
) {
    fun getPendingCount(): Flow<Int> = notificationDao.getPendingCount()
    fun getSyncingCount(): Flow<Int> = notificationDao.getSyncingCount()
    fun getFailedCount(): Flow<Int> = notificationDao.getFailedCount()
    fun getSyncedCount(): Flow<Int> = notificationDao.getSyncedCount()
    fun getTotalCount(): Flow<Int> = notificationDao.getTotalCount()
    
    fun getAllNotificationsDesc(): Flow<List<NotificationEvent>> = notificationDao.getAllNotificationsDesc()
}
