// SPDX-License-Identifier: Apache-2.0
package rs.lausevic.stow.data.db

import androidx.room.TypeConverter
import rs.lausevic.stow.data.model.Accommodation
import rs.lausevic.stow.data.model.Bag
import rs.lausevic.stow.data.model.ItemKind
import rs.lausevic.stow.data.model.PackStatus
import rs.lausevic.stow.data.model.RuleType
import rs.lausevic.stow.data.model.SectionPhase
import rs.lausevic.stow.data.model.TripMode

/*
 * Enumi se čuvaju kao IMENA, ne kao redni brojevi. Redni broj bi značio da umetanje
 * nove vrednosti u sredinu enuma tiho prepiše značenje svakog postojećeg reda.
 *
 * Vremena su epoch millis (Long), datumi epoch dan (Long) — java.time postoji nativno
 * od API 26, koji je i naš minSdk, pa nema ni desugaringa ni dodatne zavisnosti.
 */
class Converters {

    @TypeConverter fun kindToString(v: ItemKind): String = v.name

    @TypeConverter fun stringToKind(v: String): ItemKind =
        runCatching { ItemKind.valueOf(v) }.getOrDefault(ItemKind.ITEM)

    @TypeConverter fun bagToString(v: Bag): String = v.name

    @TypeConverter fun stringToBag(v: String): Bag =
        runCatching { Bag.valueOf(v) }.getOrDefault(Bag.UNASSIGNED)

    @TypeConverter fun bagToStringNullable(v: Bag?): String? = v?.name

    @TypeConverter fun stringToBagNullable(v: String?): Bag? =
        v?.let { runCatching { Bag.valueOf(it) }.getOrNull() }

    @TypeConverter fun statusToString(v: PackStatus): String = v.name

    @TypeConverter fun stringToStatus(v: String): PackStatus =
        runCatching { PackStatus.valueOf(v) }.getOrDefault(PackStatus.TO_PACK)

    @TypeConverter fun phaseToString(v: SectionPhase): String = v.name

    @TypeConverter fun stringToPhase(v: String): SectionPhase =
        runCatching { SectionPhase.valueOf(v) }.getOrDefault(SectionPhase.PACKING)

    @TypeConverter fun modeToString(v: TripMode): String = v.name

    @TypeConverter fun stringToMode(v: String): TripMode =
        runCatching { TripMode.valueOf(v) }.getOrDefault(TripMode.PACKING)

    @TypeConverter fun accommodationToString(v: Accommodation): String = v.name

    @TypeConverter fun stringToAccommodation(v: String): Accommodation =
        runCatching { Accommodation.valueOf(v) }.getOrDefault(Accommodation.HOTEL)

    @TypeConverter fun ruleTypeToString(v: RuleType): String = v.name

    @TypeConverter fun stringToRuleType(v: String): RuleType =
        runCatching { RuleType.valueOf(v) }.getOrDefault(RuleType.UNSPECIFIED)

    @TypeConverter fun ruleTypeToStringNullable(v: RuleType?): String? = v?.name

    @TypeConverter fun stringToRuleTypeNullable(v: String?): RuleType? =
        v?.let { runCatching { RuleType.valueOf(it) }.getOrNull() }
}
