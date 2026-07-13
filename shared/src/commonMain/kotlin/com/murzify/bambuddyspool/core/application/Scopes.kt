package com.murzify.bambuddyspool.core.application

import dev.zacsweers.metro.Scope

/** Metro scope for dependencies retained for the application lifetime. */
@Scope
@Target(AnnotationTarget.CLASS)
annotation class ApplicationScope

/** Metro scope for dependencies retained by one shared component graph. */
@Scope
@Target(AnnotationTarget.CLASS)
annotation class ComponentScope
