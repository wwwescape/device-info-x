package com.wwwescape.deviceinfox.console.data.pairing

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** [ServerPairingRepository] replaced [MockPairingRepository] here in Phase 11 — the whole
 * point of the interface was that this is the only thing that had to change. */
@Module
@InstallIn(SingletonComponent::class)
abstract class PairingModule {
    @Binds
    @Singleton
    abstract fun bindPairingRepository(impl: ServerPairingRepository): PairingRepository
}
