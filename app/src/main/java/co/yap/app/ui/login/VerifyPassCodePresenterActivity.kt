package co.yap.app.ui.login

import android.content.Intent
import android.os.Bundle
import co.yap.app.R
import co.yap.modules.auth.loginpasscode.VerifyPassCodeEnum
import co.yap.modules.auth.loginpasscode.VerifyPasscodeFragment
import co.yap.modules.others.helper.Constants.REQUEST_CODE
import co.yap.yapcore.IFragmentHolder
import co.yap.yapcore.constants.Constants.EXTRA
import co.yap.yapcore.defaults.DefaultActivity
import co.yap.yapcore.managers.SessionManager

/**
 * how to use startActivityForResult(Intent(context, VerifyPassCodePresenterActivity::class.java),VerifyPassCodePresenterActivity.START_REQUEST_CODE)
 */
class VerifyPassCodePresenterActivity : DefaultActivity(), IFragmentHolder {


    private lateinit var fragment: VerifyPasscodeFragment
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_verify_pass_code_presenter)
        val ft = supportFragmentManager.beginTransaction()
        fragment = VerifyPasscodeFragment()
        val bundle = intent?.getBundleExtra(EXTRA)
        bundle?.putString("username", SessionManager.user?.currentCustomer?.getFullName())
        bundle?.putString(REQUEST_CODE, VerifyPassCodeEnum.VERIFY.name)
        fragment.arguments = bundle
        ft.replace(R.id.container, fragment)
        ft.commit()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        fragment.onActivityResult(requestCode, resultCode, data)
    }

}