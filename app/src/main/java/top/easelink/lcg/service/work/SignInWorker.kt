package top.easelink.lcg.service.work

import android.content.Context
import androidx.core.content.edit
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.Operation
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import top.easelink.lcg.BuildConfig
import top.easelink.lcg.account.UserDataRepo
import top.easelink.lcg.utils.SharedPreferencesHelper
import top.easelink.lcg.utils.SignInManager
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class SignInWorker(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        if (!UserDataRepo.isLoggedIn) {
            Timber.d("Sign in skipped: not logged in")
            return Result.success()
        }
        
        if (isRecentlyExecuted()) {
            Timber.d("Sign in skipped: recently executed")
            return Result.success()
        }

        return try {
            when (performWebViewSignIn()) {
                SignInResult.SUCCESS, SignInResult.ALREADY_DONE -> {
                    markSignInDone()
                    Result.success()
                }
                SignInResult.LOGIN_REQUIRED -> {
                    Timber.w("Sign in failed: login required")
                    Result.failure()
                }
                SignInResult.FAILED -> {
                    Timber.w("Sign in failed, will retry")
                    Result.retry()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Sign in worker exception")
            Result.failure()
        }
    }

    private suspend fun performWebViewSignIn(): SignInResult = suspendCancellableCoroutine { continuation ->
        SignInManager.performSignIn(applicationContext) { status, _ ->
            if (continuation.isActive) {
                val result = when (status) {
                    "SUCCESS" -> SignInResult.SUCCESS
                    "ALREADY_DONE" -> SignInResult.ALREADY_DONE
                    "LOGIN_REQUIRED" -> SignInResult.LOGIN_REQUIRED
                    else -> SignInResult.FAILED
                }
                continuation.resume(result)
            }
        }
    }

    enum class SignInResult { SUCCESS, ALREADY_DONE, LOGIN_REQUIRED, FAILED }

    companion object {
        private val WORK_INTERVAL: Long = if (BuildConfig.DEBUG) 15L else 8L
        private val DEFAULT_TIME_UNIT = if (BuildConfig.DEBUG) TimeUnit.SECONDS else TimeUnit.HOURS

        const val TAG = "SignInWorker"
        private const val SP_KEY_LAST_SIGN_IN_AT = "sp_key_last_sign_in_at"
        private const val DEDUP_WINDOW_MS = 7L * 60 * 60 * 1000

        fun isRecentlyExecuted(): Boolean {
            val sp = SharedPreferencesHelper.getUserSp()
            val last = sp.getLong(SP_KEY_LAST_SIGN_IN_AT, 0L)
            return System.currentTimeMillis() - last < DEDUP_WINDOW_MS
        }

        private fun markSignInDone() {
            SharedPreferencesHelper.getUserSp().edit {
                putLong(SP_KEY_LAST_SIGN_IN_AT, System.currentTimeMillis())
            }
        }

        fun startSignInWork(context: Context): Operation {
            val constraints = Constraints.Builder()
                .setRequiresCharging(false)
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(false)
                .build()

            val request = PeriodicWorkRequest.Builder(
                SignInWorker::class.java,
                WORK_INTERVAL,
                DEFAULT_TIME_UNIT
            )
                .setConstraints(constraints)
                .addTag(TAG)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 15L, TimeUnit.MINUTES)
                .build()

            return WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                TAG,
                ExistingPeriodicWorkPolicy.UPDATE, // 改为 UPDATE 确保生效
                request
            )
        }
    }
}
