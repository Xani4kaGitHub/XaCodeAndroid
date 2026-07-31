package com.xanichka.xacode.ui

import com.xanichka.xacode.model.UiLanguage

internal fun tr(language: UiLanguage, russian: String, ukrainian: String, english: String): String = when (language) {
    UiLanguage.RUSSIAN -> russian
    UiLanguage.UKRAINIAN -> ukrainian
    UiLanguage.ENGLISH -> english
}
