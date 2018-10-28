package com.priyankvasa.android.cameraviewex

import android.support.test.rule.GrantPermissionRule
import org.junit.Rule

open class GrantPermissionsRule {

    @get:Rule
    val grantPermissionRule: GrantPermissionRule = GrantPermissionRule.grant(
            android.Manifest.permission.CAMERA
    )
}
