package co.yap.widgets.guidedtour.description

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import co.yap.yapcore.R
import java.lang.ref.WeakReference
import java.util.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt


class BubbleMessageView : ConstraintLayout {

    private val WIDTH_ARROW = 20

    private var itemView: View? = null

    private var textViewTitle: TextView? = null
    private var textViewSubtitle: TextView? = null
    private var textViewCounter: TextView? = null
    private var btnNext: Button? = null
    private var showCaseMessageViewLayout: ConstraintLayout? = null

    private var targetViewScreenLocation: RectF? = null
    private var mBackgroundColor: Int = ContextCompat.getColor(context, R.color.white)
    private var arrowPositionList = ArrayList<BubbleShowCase.ArrowPosition>()

    private var paint: Paint? = null

    constructor(context: Context) : super(context) {
        initView()
    }

    constructor(context: Context, builder: Builder) : super(context) {
        initView()
        setAttributes(builder)
        setBubbleListener(builder)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initView()
    }

    private fun initView() {
        setWillNotDraw(false)
        inflateXML()
        bindViews()
    }

    private fun inflateXML() {
        itemView = inflate(context, R.layout.couch_mark_message_box, this)
    }

    private fun bindViews() {
        btnNext = findViewById(R.id.btnNext)
        textViewTitle = findViewById(R.id.tvTitle)
        textViewSubtitle = findViewById(R.id.tvDescription)
        textViewCounter = findViewById(R.id.tvCount)
        showCaseMessageViewLayout = findViewById(R.id.showCaseMessageViewLayout)
    }

    fun setAttributes(builder: Builder) {
        if (builder.mCloseAction != null) {
            btnNext?.visibility = View.VISIBLE
        }

        if (builder.mDisableCloseAction != null && builder.mDisableCloseAction!!) {
            btnNext?.visibility = View.INVISIBLE
        }

        builder.mTitle?.let {
            textViewTitle?.visibility = View.VISIBLE
            textViewTitle?.text = builder.mTitle
        }
        builder.mSubtitle?.let {
            textViewSubtitle?.visibility = View.VISIBLE
            textViewSubtitle?.text = builder.mSubtitle
        }
        builder.mPageNo?.let {
            textViewCounter?.visibility = View.VISIBLE
            textViewCounter?.text = builder.mPageNo
        }
        builder.mTextColor?.let {
            textViewTitle?.setTextColor(builder.mTextColor!!)
            textViewSubtitle?.setTextColor(builder.mTextColor!!)
        }
        builder.mTitleTextSize?.let {
            textViewTitle?.setTextSize(
                TypedValue.COMPLEX_UNIT_SP,
                builder.mTitleTextSize!!.toFloat()
            )
        }
        builder.mSubtitleTextSize?.let {
            textViewSubtitle?.setTextSize(
                TypedValue.COMPLEX_UNIT_SP,
                builder.mSubtitleTextSize!!.toFloat()
            )
        }
        builder.mBackgroundColor?.let { mBackgroundColor = builder.mBackgroundColor!! }

        builder.mBtnText?.let { btnNext?.text = it }
        builder.mPageNoShow?.let {
            if (!it)
                textViewCounter?.visibility = View.INVISIBLE
        }
        arrowPositionList = builder.mArrowPosition
        targetViewScreenLocation = builder.mTargetViewScreenLocation
    }

    private fun setBubbleListener(builder: Builder) {
        btnNext?.setOnClickListener { builder.mListener?.onCloseActionImageClick() }
        itemView?.setOnClickListener { builder.mListener?.onBubbleClick() }
    }


    //END REGION

    //REGION AUX FUNCTIONS

    private fun getViewWidth(): Int = width

    private fun getMargin(): Int = TourUtils.dpToPx(20)

    private fun getSecurityArrowMargin(): Int = getMargin() + TourUtils.dpToPx(2 * WIDTH_ARROW / 3)

    //END REGION

    //REGION SHOW ITEM

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        prepareToDraw()
        drawRectangle(canvas)

        for (arrowPosition in arrowPositionList) {
            drawArrow(canvas, arrowPosition, targetViewScreenLocation)
        }
    }

    private fun prepareToDraw() {
        paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint!!.color = mBackgroundColor
        paint!!.style = Paint.Style.FILL
        paint!!.strokeWidth = 4.0f
    }

    private fun drawRectangle(canvas: Canvas) {
        val rect = RectF(
            getMargin().toFloat(),
            getMargin().toFloat(),
            getViewWidth() - getMargin().toFloat(),
            height - getMargin().toFloat()
        )
        canvas.drawRoundRect(
            rect,
            context.resources.getDimension(R.dimen._10sdp),
            context.resources.getDimension(R.dimen._10sdp),
            paint!!
        )
    }

    private fun drawArrow(
        canvas: Canvas,
        arrowPosition: BubbleShowCase.ArrowPosition,
        targetViewLocationOnScreen: RectF?
    ) {
        val xPosition: Int
        val yPosition: Int
        when (arrowPosition) {
            BubbleShowCase.ArrowPosition.LEFT -> {
                xPosition = getMargin()
                yPosition =
                    targetViewLocationOnScreen?.let {
                        calculateMaxMinVerticalPositionTip(
                            getArrowHorizontalPositionDependingOnTarget(it),
                            height
                        )
                    } ?: height / 2
            }
            BubbleShowCase.ArrowPosition.RIGHT -> {
                xPosition = getViewWidth() - getMargin()
                yPosition =
                    targetViewLocationOnScreen?.let {
                        calculateMaxMinVerticalPositionTip(
                            getArrowHorizontalPositionDependingOnTarget(it),
                            height
                        )
                    } ?: height / 2
            }
            BubbleShowCase.ArrowPosition.TOP -> {
                xPosition =
                    targetViewLocationOnScreen?.let {
                        calculateMaxMinHorizontalPositionTip(
                            getArrowHorizontalPositionDependingOnTarget(it),
                            width
                        )
                    } ?: width / 2
                yPosition = getMargin()
            }
            BubbleShowCase.ArrowPosition.BOTTOM -> {
                xPosition =
                    targetViewLocationOnScreen?.let {
                        calculateMaxMinHorizontalPositionTip(
                            getArrowHorizontalPositionDependingOnTarget(it),
                            width
                        )
                    } ?: width / 2
                yPosition = height - getMargin()
            }
        }

        drawRhombus(canvas, paint, xPosition, yPosition, TourUtils.dpToPx(WIDTH_ARROW))
    }

    private fun getArrowHorizontalPositionDependingOnTarget(targetViewLocationOnScreen: RectF?): Int {
        return when {
            isOutOfRightBound(targetViewLocationOnScreen) -> width - getSecurityArrowMargin()
            isOutOfLeftBound(targetViewLocationOnScreen) -> getSecurityArrowMargin()
            else -> (targetViewLocationOnScreen!!.centerX() - TourUtils.getAxisXpositionOfViewOnScreen(
                this
            )).roundToInt()
        }
    }

    private fun calculateMaxMinHorizontalPositionTip(tipPos: Int, dim: Int): Int {
        val leftOffSetX = dim.div(100).times(20)
        val rightOffSetX = dim.div(100).times(20)
        val safeLeft = max(leftOffSetX, tipPos)
        return min((dim - rightOffSetX), safeLeft)
    }

    private fun calculateMaxMinVerticalPositionTip(tipPos: Int, dim: Int): Int {
        val topOffSetX = dim.div(100).times(15)
        val bottomOffSetX = dim.div(100).times(15)
        val safeTop = max(topOffSetX, tipPos)
        return min((dim - bottomOffSetX), safeTop)
    }

    private fun getArrowVerticalPositionDependingOnTarget(targetViewLocationOnScreen: RectF?): Int {
        return when {
            isOutOfBottomBound(targetViewLocationOnScreen) -> height - getSecurityArrowMargin()
            isOutOfTopBound(targetViewLocationOnScreen) -> getSecurityArrowMargin()
            else -> (targetViewLocationOnScreen!!.centerY() + TourUtils.getStatusBarHeight(context)
                    -
                    TourUtils.getAxisYpositionOfViewOnScreen(this)).roundToInt()
        }
    }

    private fun isOutOfRightBound(targetViewLocationOnScreen: RectF?): Boolean {
        return targetViewLocationOnScreen!!.centerX() > TourUtils.getAxisXpositionOfViewOnScreen(
            this
        ) + width - getSecurityArrowMargin()
    }

    private fun isOutOfLeftBound(targetViewLocationOnScreen: RectF?): Boolean {
        return targetViewLocationOnScreen!!.centerX() < TourUtils.getAxisXpositionOfViewOnScreen(
            this
        ) + getSecurityArrowMargin()
    }

    private fun isOutOfBottomBound(targetViewLocationOnScreen: RectF?): Boolean {
        return targetViewLocationOnScreen!!.centerY() > TourUtils.getAxisYpositionOfViewOnScreen(
            this
        ) + height - getSecurityArrowMargin() - TourUtils.getStatusBarHeight(context)
    }

    private fun isOutOfTopBound(targetViewLocationOnScreen: RectF?): Boolean {
        return targetViewLocationOnScreen!!.centerY() < TourUtils.getAxisYpositionOfViewOnScreen(
            this
        ) + getSecurityArrowMargin() - TourUtils.getStatusBarHeight(context)
    }


    private fun drawRhombus(canvas: Canvas, paint: Paint?, x: Int, y: Int, width: Int) {
        val halfRhombusWidth = width / 2

        val path = Path()
        path.moveTo(x.toFloat(), (y + halfRhombusWidth).toFloat()) // Top
        path.lineTo((x - halfRhombusWidth).toFloat(), y.toFloat()) // Left
        path.lineTo(x.toFloat(), (y - halfRhombusWidth).toFloat()) // Bottom
        path.lineTo((x + halfRhombusWidth).toFloat(), y.toFloat()) // Right
        path.lineTo(x.toFloat(), (y + halfRhombusWidth).toFloat()) // Back to Top
        path.close()

        canvas.drawPath(path, paint!!)
    }


    //END REGION

    /**
     * Builder for BubbleMessageView class
     */
    class Builder(private val activity: Activity) {
        var bubbleMessageView: BubbleMessageView? = null
        lateinit var mContext: WeakReference<Context>
        var mTargetViewScreenLocation: RectF? = null
        var mImage: Drawable? = null
        var mDisableCloseAction: Boolean? = null
        var mTitle: String? = null
        var mSubtitle: String? = null
        var mPageNo: String? = null
        var mCloseAction: Drawable? = null
        var mBackgroundColor: Int? = null
        var mTextColor: Int? = null
        var mTitleTextSize: Int? = null
        var mSubtitleTextSize: Int? = null
        var mArrowPosition = ArrayList<BubbleShowCase.ArrowPosition>()
        var mListener: OnBubbleMessageViewListener? = null

        var mPageNoShow: Boolean? = null
        var mSkipShow: Boolean? = null
        var mBtnText: String? = null


        fun from(context: Context): Builder {
            mContext = WeakReference(context)
            return this
        }

        fun title(title: String?): Builder {
            mTitle = title
            return this
        }

        fun subtitle(subtitle: String?): Builder {
            mSubtitle = subtitle
            return this
        }

        fun pageNo(page: String?): Builder {
            mPageNo = page
            return this
        }

        fun image(image: Drawable?): Builder {
            mImage = image
            return this
        }

        fun closeActionImage(image: Drawable?): Builder {
            mCloseAction = image
            return this
        }

        fun disableCloseAction(isDisabled: Boolean): Builder {
            mDisableCloseAction = isDisabled
            return this
        }

        fun targetViewScreenLocation(targetViewLocationOnScreen: RectF): Builder {
            mTargetViewScreenLocation = targetViewLocationOnScreen
            return this
        }

        fun backgroundColor(backgroundColor: Int?): Builder {
            mBackgroundColor = backgroundColor
            return this
        }

        fun textColor(textColor: Int?): Builder {
            mTextColor = textColor
            return this
        }

        fun titleTextSize(textSize: Int?): Builder {
            mTitleTextSize = textSize
            return this
        }

        fun subtitleTextSize(textSize: Int?): Builder {
            mSubtitleTextSize = textSize
            return this
        }

        fun arrowPosition(arrowPosition: List<BubbleShowCase.ArrowPosition>): Builder {
            mArrowPosition.clear()
            mArrowPosition.addAll(arrowPosition)
            return this
        }

        fun listener(listener: OnBubbleMessageViewListener?): Builder {
            mListener = listener
            return this
        }

        fun build(): BubbleMessageView {
            return BubbleMessageView(mContext.get()!!, this)
        }

        fun showPageNo(isPageNoShow: Boolean): Builder {
            mPageNoShow = isPageNoShow
            return this
        }

        fun showSkip(isSkipShow: Boolean): Builder {
            mSkipShow = isSkipShow
            return this
        }

        fun btnText(btnText: String?): Builder {
            mBtnText = btnText
            return this
        }

        init {
            bubbleMessageView = BubbleMessageView(activity)
        }
    }
}