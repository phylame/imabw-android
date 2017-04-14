package pw.phylame.imabw

import android.content.Context
import android.support.annotation.StringRes
import android.support.design.widget.TextInputLayout
import android.support.v7.app.AlertDialog
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import pw.phylame.support.tintBackground
import pw.phylame.support.viewFor

class InputBuilder(context: Context) : AlertDialog.Builder(context) {
    companion object {
        const val VIEW_ID = R.layout.dialog_input
    }

    private var _text: Any? = null
    private var _buttonId: Int? = null
    private var _tintColor: Int? = null
    private var _hintText: CharSequence? = null
    private var _listener: ((String) -> Unit)? = null

    fun setText(o: Any): InputBuilder {
        _text = o
        return this
    }

    fun setTint(tint: Int): InputBuilder {
        _tintColor = tint
        return this
    }

    fun setHint(hint: CharSequence): InputBuilder {
        _hintText = hint
        return this
    }

    fun setHint(@StringRes hintId: Int): InputBuilder {
        setHint(context.getString(hintId))
        return this
    }

    fun setListener(textId: Int, listener: (String) -> Unit): InputBuilder {
        _buttonId = textId
        _listener = listener
        return this
    }

    private lateinit var editText: EditText
    private lateinit var inputLayout: TextInputLayout
    private lateinit var dialog: AlertDialog
    private lateinit var okButton: Button

    override fun create(): AlertDialog {
        inputLayout = View.inflate(context, VIEW_ID, null) as TextInputLayout
        editText = inputLayout.viewFor(R.id.text)
        editText.setText(_text?.toString())
        editText.selectAll()
        inputLayout.hint = _hintText
        setView(inputLayout)
        editText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                okButton.isEnabled = s.isNotEmpty()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        editText.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE && editText.text.isNotEmpty()) {
                dialog.dismiss()
                _listener?.invoke(editText.text.toString())
                true
            } else {
                false
            }
        }
        if (_buttonId != null) {
            setPositiveButton(_buttonId!!) { _, _ ->
                _listener?.invoke(editText.text.toString())
            }
        }
        dialog = super.create()
        return dialog
    }

    override fun show(): AlertDialog {
        val dialog = super.show()
        val tintColor = _tintColor
        if (tintColor != null) {
            inputLayout.tintBackground(tintColor)
            editText.tintBackground(tintColor)
            editText.setHintTextColor(tintColor)
            okButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            okButton.isEnabled = editText.text.isNotEmpty()
            okButton.setTextColor(tintColor)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(tintColor)
        }
        return dialog
    }
}