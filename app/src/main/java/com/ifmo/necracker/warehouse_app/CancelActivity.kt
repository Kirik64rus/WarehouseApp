package com.ifmo.necracker.warehouse_app

import android.os.AsyncTask
import android.os.Bundle
import android.os.PersistableBundle
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.ifmo.necracker.warehouse_app.model.Order
import com.ifmo.necracker.warehouse_app.model.Request
import com.ifmo.necracker.warehouse_app.model.TaskStatus
import com.ifmo.necracker.warehouse_app.model.User
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestClientException

class CancelActivity : AppCompatActivity() {

    private var orderId: TextView? = null
    private var itemId: TextView? = null
    private var itemAmount: TextView? = null
    private var orderType: TextView? = null
    private var orderStatus: TextView? = null
    private var buyButton: Button? = null
    private var cancelButton: Button? = null
    private var user: User? = null
    private var order: Order? = null
    private var asyncTask: AsyncTask<Void, Void, Boolean>? = null
    private val restTemplate = com.ifmo.necracker.warehouse_app.restTemplate.restTemplate
    private var progressView: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cancel)
        buyButton = findViewById(R.id.buyButton) as Button
        cancelButton = findViewById(R.id.cancelButton) as Button

        orderId = findViewById(R.id.actionOrderId) as TextView
        itemId = findViewById(R.id.actionOrderItemId) as TextView
        itemAmount = findViewById(R.id.actionOrderAmount) as TextView
        orderStatus = findViewById(R.id.actionOrderStatus) as TextView
        orderType = findViewById(R.id.actionOrderType) as TextView
        progressView = findViewById(R.id.order_progress)


        user = intent.getSerializableExtra("user") as User
        order = intent.getSerializableExtra("order") as Order
        orderId!!.text = String.format("Order" + getString(R.string.string_id), order!!.id)
        itemId!!.text = order!!.name
        itemAmount!!.text = String.format(getString(R.string.string_amount), order!!.amount)
        orderType!!.text = String.format(getString(R.string.string_type), order!!.type)
        orderStatus!!.text = String.format(getString(R.string.string_status), order!!.status)

        if (savedInstanceState != null) {
            asyncTask = lastCustomNonConfigurationInstance?.let { it as ListActivity.GetAllItemsTask }
            asyncTask?.let {
                if (asyncTask is BuyOrder) {
                    (asyncTask as BuyOrder).activity = this
                    if ((asyncTask as BuyOrder).status == TaskStatus.PROCESSING) {
                        progressView!!.visibility = View.VISIBLE
                    }
                } else {
                    (asyncTask as CancelOrder).activity = this
                    if ((asyncTask as CancelOrder).status == TaskStatus.PROCESSING) {
                        progressView!!.visibility = View.VISIBLE
                    }
                }
            }
        }
        if (order!!.type == Request.RequestType.BOOKED && order!!.status == Request.RequestStatus.DONE) {
            buyButton!!.setOnClickListener {
                if (asyncTask == null) {
                    asyncTask = BuyOrder(order!!.id, this)
                    progressView!!.visibility = View.VISIBLE
                    asyncTask!!.execute(null)
                } else {
                    makeToast(this, "Other operation in progress")
                }
            }
        } else {
            buyButton!!.isEnabled = false
        }
        if (order!!.type == Request.RequestType.BOOKED) {
            cancelButton!!.setOnClickListener {
                if (asyncTask == null) {
                    asyncTask = CancelOrder(order!!.id, this)
                    progressView!!.visibility = View.VISIBLE
                    asyncTask!!.execute(null)
                } else {
                    makeToast(this, "Other operation in progress")
                }
            }
        } else {
            cancelButton!!.isEnabled = false
        }
    }

    override fun onRetainCustomNonConfigurationInstance(): Any? {
        return asyncTask
    }

    override fun onSaveInstanceState(outState: Bundle?, outPersistentState: PersistableBundle?) {
        super.onSaveInstanceState(outState, outPersistentState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle?) {
        super.onRestoreInstanceState(savedInstanceState)
        asyncTask?.let {
            if (asyncTask is BuyOrder) {
                (asyncTask as BuyOrder).activity = this
                if ((asyncTask as BuyOrder).status == TaskStatus.PROCESSING) {
                    progressView!!.visibility = View.VISIBLE
                }
            } else {
                (asyncTask as CancelOrder).activity = this
                if ((asyncTask as CancelOrder).status == TaskStatus.PROCESSING) {
                    progressView!!.visibility = View.VISIBLE
                }
            }
        }
    }

    inner class BuyOrder internal constructor(private val id: Long, var activity: CancelActivity) : AsyncTask<Void, Void, Boolean>() {
        private var error = ""
        var status = TaskStatus.NONE
        override fun doInBackground(vararg params: Void): Boolean? {
            status = TaskStatus.PROCESSING
            for (attempt in 1..MAX_ATTEMPTS_COUNT) {
                try {
                    restTemplate.put("$serverAddress/payment/$id", null)
                } catch (e: HttpStatusCodeException) {
                    error = "Error during buying"
                    return false
                } catch (e: RestClientException) {
                    if (attempt == MAX_ATTEMPTS_COUNT) {
                        error = "Unable to connect to server"
                        return false
                    }
                }
            }
            return true
        }

        override fun onPostExecute(success: Boolean?) {
            status = TaskStatus.READY
            activity.progressView!!.visibility = View.GONE
            if (!success!!) {
                makeToast(activity.applicationContext, error)
            } else {
                makeToast(activity.applicationContext, "Success!")
                activity.order!!.type = Request.RequestType.PAID
                activity.order!!.status = Request.RequestStatus.IN_PROGRESS
                activity.orderType!!.text = String.format(getString(R.string.string_type), order!!.type)
                activity.orderStatus!!.text = String.format(getString(R.string.string_status), order!!.status)
                activity.buyButton!!.isEnabled = false
                activity.cancelButton!!.isEnabled = false
                activity.intent.putExtra("order", order)
            }
            activity.asyncTask = null
        }

        override fun onCancelled() {
            status = TaskStatus.READY
            activity.progressView!!.visibility = View.GONE
            activity.asyncTask = null
        }
    }

    inner class CancelOrder internal constructor(private val id: Long, var activity: CancelActivity) : AsyncTask<Void, Void, Boolean>() {
        private var error = ""
        var status = TaskStatus.NONE
        override fun doInBackground(vararg params: Void): Boolean? {
            status = TaskStatus.PROCESSING
            for (attempt in 1..MAX_ATTEMPTS_COUNT) {
                try {
                    restTemplate.put("$serverAddress/cancellation/$id", null)
                } catch (e: HttpStatusCodeException) {
                    error = "Error during cancellation"
                    return false
                } catch (e: RestClientException) {
                    if (attempt == MAX_ATTEMPTS_COUNT) {
                        error = "Unable to connect to server"
                        return false
                    }
                }
            }
            return true
        }

        override fun onPostExecute(success: Boolean?) {
            status = TaskStatus.READY
            activity.progressView!!.visibility = View.GONE
            if (!success!!) {
                makeToast(activity.applicationContext, error)
            } else {
                makeToast(activity.applicationContext, "Success!")
                activity.order!!.type = Request.RequestType.CANCELED
                activity.order!!.status = Request.RequestStatus.CANCELED
                activity.orderType!!.text = String.format(getString(R.string.string_type), order!!.type)
                activity.orderStatus!!.text = String.format(getString(R.string.string_status), order!!.status)
                activity.buyButton!!.isEnabled = false
                activity.cancelButton!!.isEnabled = false
                activity.intent.putExtra("order", order)
            }
            activity.asyncTask = null
        }

        override fun onCancelled() {
            activity.progressView!!.visibility = View.GONE
            status = TaskStatus.READY
            activity.asyncTask = null
        }
    }
}
