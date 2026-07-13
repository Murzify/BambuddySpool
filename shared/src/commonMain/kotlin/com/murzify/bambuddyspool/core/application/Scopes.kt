package com.murzify.bambuddyspool.core.application

import dev.zacsweers.metro.Scope

@Scope
@Target(AnnotationTarget.CLASS)
annotation class ApplicationScope

@Scope
@Target(AnnotationTarget.CLASS)
annotation class ComponentScope
