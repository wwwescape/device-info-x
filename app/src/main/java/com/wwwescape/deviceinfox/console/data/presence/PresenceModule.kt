package com.wwwescape.deviceinfox.console.data.presence

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** [ServerPresenceRepository] replaced [MockPresenceRepository] here in Phase 11.5 — the whole
 * point of the interface was that this is the only thing that had to change. */
@Module
@InstallIn(SingletonComponent::class)
abstract class PresenceModule {
    @Binds
    @Singleton
    abstract fun bindPresenceRepository(impl: ServerPresenceRepository): PresenceRepository
}
