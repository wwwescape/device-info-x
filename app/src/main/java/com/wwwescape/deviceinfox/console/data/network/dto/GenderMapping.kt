package com.wwwescape.deviceinfox.console.data.network.dto

import com.wwwescape.deviceinfox.console.data.db.PartnerGender

fun GenderDto.toPartnerGender(): PartnerGender = when (this) {
    GenderDto.MALE -> PartnerGender.MALE
    GenderDto.FEMALE -> PartnerGender.FEMALE
    GenderDto.UNSPECIFIED -> PartnerGender.UNSPECIFIED
}

fun PartnerGender.toGenderDto(): GenderDto = when (this) {
    PartnerGender.MALE -> GenderDto.MALE
    PartnerGender.FEMALE -> GenderDto.FEMALE
    PartnerGender.UNSPECIFIED -> GenderDto.UNSPECIFIED
}
