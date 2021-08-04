package com.rnlib.adyen.ui.component

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.isVisible
import androidx.core.widget.ContentLoadingProgressBar
import androidx.recyclerview.widget.RecyclerView
import com.adyen.checkout.card.*
import com.adyen.checkout.card.data.CardType
import com.adyen.checkout.components.PaymentComponentState
import com.adyen.checkout.components.api.ImageLoader
import com.adyen.checkout.components.model.payments.request.PaymentMethodDetails
import com.adyen.checkout.components.util.PaymentMethodTypes
import com.adyen.checkout.core.exception.CheckoutException
import com.adyen.checkout.core.log.LogUtil
import com.adyen.checkout.core.log.Logger
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.rnlib.adyen.R
import com.rnlib.adyen.ui.base.BaseComponentDialogFragment

class CardComponentDialogFragment : BaseComponentDialogFragment() {

    companion object : BaseCompanion<CardComponentDialogFragment>(CardComponentDialogFragment::class.java) {
        private val TAG = LogUtil.getTag()
    }

    private lateinit var cardListAdapter: CardListAdapter

    private lateinit var header: TextView
    private lateinit var payButton: Button
    private lateinit var cardView: SpinCardView
    private lateinit var progressBar: ContentLoadingProgressBar
    private lateinit var recyclerViewCardList: RecyclerView
    private lateinit var switchCompat: SwitchCompat

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.frag_card_component, container, false)


        cardView = view.findViewById(R.id.cardView)
        header = view.findViewById(R.id.header)
        payButton = view.findViewById(R.id.payButton)
        progressBar = view.findViewById(R.id.progressBar)
        recyclerViewCardList = view.findViewById(R.id.recyclerView_cardList)
        switchCompat = cardView.findViewById(R.id.switch_storePaymentMethod)
        return view
    }

    override fun setPaymentPendingInitialization(pending: Boolean) {
        payButton.isVisible = !pending
        if (pending) progressBar.show()
        else progressBar.hide()
    }

    override fun highlightValidationErrors() {
        cardView.highlightValidationErrors()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            component as CardComponent
        } catch (e: ClassCastException) {
            throw CheckoutException("Component is not CardComponent")
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Logger.d(TAG, "onViewCreated")

        val cardComponent = component as CardComponent

        if (!adyenComponentConfiguration.amount.isEmpty) {
            payButton.text = getString(R.string.add_card)
        }

        // Keeping generic component to use the observer from the BaseComponentDialogFragment
        component.observe(viewLifecycleOwner, this)
        cardComponent.observeErrors(viewLifecycleOwner, createErrorHandlerObserver())

        // try to get the name from the payment methods response
        header.text =
            adyenComponentViewModel.paymentMethodsApiResponse.paymentMethods?.find { it.type == PaymentMethodTypes.SCHEME }?.name

        cardView.attach(cardComponent, viewLifecycleOwner)

        if (cardView.isConfirmationRequired) {
            payButton.setOnClickListener { componentDialogViewModel.payButtonClicked() }
            setInitViewState(BottomSheetBehavior.STATE_EXPANDED)
            cardView.requestFocus()
        } else {
            payButton.visibility = View.GONE
        }

        val supportedCards = if (cardComponent.isStoredPaymentMethod()) {
            emptyList<CardType>()
        } else {
            cardComponent.configuration.supportedCardTypes
        }
        cardListAdapter = CardListAdapter(
            ImageLoader.getInstance(requireContext(), (component as CardComponent).configuration.environment),
            supportedCards
        )
        recyclerViewCardList.adapter = cardListAdapter

        bindAddCardButtonWithAuthorizeSwitch()
    }

    override fun onChanged(paymentComponentState: PaymentComponentState<in PaymentMethodDetails>?) {
        val cardComponent = component as CardComponent
        val cardComponentState = paymentComponentState as? CardComponentState
        if (cardComponentState?.cardType != null && !cardComponent.isStoredPaymentMethod()) {
            // TODO: 11/01/2021 pass list of cards from Bin Lookup
            cardListAdapter.setFilteredCard(listOf(cardComponentState.cardType))
        } else {
            cardListAdapter.setFilteredCard(emptyList())
        }

        componentDialogViewModel.componentStateChanged((component as CardComponent).state)
    }

    private fun bindAddCardButtonWithAuthorizeSwitch() {
        switchCompat.isChecked = true

        payButton.isEnabled = switchCompat.isChecked
        switchCompat.setOnCheckedChangeListener { _, isChecked ->
            payButton.isEnabled = isChecked
        }
    }
}

