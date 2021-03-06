package fr.acinq.eclair.gui.controllers

import javafx.application.Platform
import javafx.event.ActionEvent
import javafx.fxml.FXML
import javafx.scene.control.{ComboBox, Label, TextArea, TextField}
import javafx.scene.image.{ImageView, WritableImage}
import javafx.scene.layout.GridPane
import javafx.scene.paint.Color
import javafx.stage.Stage

import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.{BarcodeFormat, EncodeHintType}
import fr.acinq.bitcoin.Satoshi
import fr.acinq.eclair.Setup
import fr.acinq.eclair.gui.Handlers
import fr.acinq.eclair.gui.utils.{ContextMenuUtils, GUIValidators}
import fr.acinq.eclair.payment.PaymentRequest
import grizzled.slf4j.Logging

import scala.util.{Failure, Success, Try}

/**
  * Created by DPA on 23/09/2016.
  */
class ReceivePaymentController(val handlers: Handlers, val stage: Stage) extends Logging {

  @FXML var amount: TextField = _
  @FXML var amountError: Label = _
  @FXML var unit: ComboBox[String] = _
  @FXML var description: TextArea = _

  @FXML var resultBox: GridPane = _
  // the content of this field is generated and readonly
  @FXML var paymentRequestTextArea: TextArea = _
  @FXML var paymentRequestQRCode: ImageView = _

  @FXML def initialize = {
    unit.setValue(unit.getItems.get(0))
    resultBox.managedProperty().bind(resultBox.visibleProperty())
    stage.sizeToScene()
  }

  @FXML def handleCopyInvoice(event: ActionEvent) = ContextMenuUtils.copyToClipboard(paymentRequestTextArea.getText)

  @FXML def handleGenerate(event: ActionEvent) = {
    if ((("milliATB".equals(unit.getValue) || "Satoshi".equals(unit.getValue) || "ATB".equals(unit.getValue))
      && GUIValidators.validate(amount.getText, amountError, "Amount must be numeric", GUIValidators.amountDecRegex))) {
      try {
        val Array(parsedInt, parsedDec) = if (amount.getText.contains(".")) amount.getText.split("\\.") else Array(amount.getText, "0")
        val amountDec = parsedDec.length match {
          case 0 => "000"
          case 1 => parsedDec.concat("00")
          case 2 => parsedDec.concat("0")
          case 3 => parsedDec
          case _ =>
            // amount has too many decimals, regex validation has failed somehow
            throw new NumberFormatException("incorrect amount")
        }
        val smartAmount = unit.getValue match {
          case "ATB" => Satoshi(parsedInt.toLong * 100000000L + amountDec.toLong * 100000L)
          case "milliATB" => Satoshi(parsedInt.toLong * 100000L + amountDec.toLong * 100L)
          case "Satoshi" => Satoshi(parsedInt.toLong)
        }
        if (GUIValidators.validate(amountError, "Amount must be greater than 0", smartAmount.amount > 0)
          && GUIValidators.validate(amountError, f"Amount must be less than ${PaymentRequest.maxAmount.amount}%,d sat (~${PaymentRequest.maxAmount.amount / 100000000D}%.3f ATB)", smartAmount <= PaymentRequest.maxAmount)
          && GUIValidators.validate(amountError, f"Amount must be more than ${PaymentRequest.minAmount.amount}%,d sat (~${PaymentRequest.minAmount.amount / 100000000D}%.3f ATB)", smartAmount >= PaymentRequest.minAmount)
          && GUIValidators.validate(amountError, "Description is too long, max 256 chars.", description.getText().size < 256)) {
          import scala.concurrent.ExecutionContext.Implicits.global
          handlers.receive(smartAmount, description.getText) onComplete {
            case Success(s) =>
              Try(createQRCode(s)) match {
                case Success(wImage) => displayPaymentRequest(s, Some(wImage))
                case Failure(t) => displayPaymentRequest(s, None)
              }
            case Failure(t) => Platform.runLater(new Runnable {
              def run = GUIValidators.validate(amountError, "The payment request could not be generated", false)
            })
          }
        }
      } catch {
        case e: NumberFormatException =>
          logger.debug(s"Could not generate payment request for amount = ${amount.getText}")
          paymentRequestTextArea.setText("")
          amountError.setText("Amount is incorrect")
          amountError.setOpacity(1)
      }
    }
  }

  private def displayPaymentRequest(pr: String, image: Option[WritableImage]) = Platform.runLater(new Runnable {
    def run = {
      paymentRequestTextArea.setText(pr)
      if ("".equals(pr)) {
        resultBox.setVisible(false)
        resultBox.setMaxHeight(0)
      } else {
        resultBox.setVisible(true)
        resultBox.setMaxHeight(Double.MaxValue)
      }
      image.map(paymentRequestQRCode.setImage(_))
      stage.sizeToScene()
    }
  })

  private def createQRCode(data: String, width: Int = 250, height: Int = 250, margin: Int = -1): WritableImage = {
    import scala.collection.JavaConversions._
    val hintMap = collection.mutable.Map[EncodeHintType, Object]()
    hintMap.put(EncodeHintType.CHARACTER_SET, "UTF-8")
    hintMap.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L)
    hintMap.put(EncodeHintType.MARGIN, margin.toString)
    val qrWriter = new QRCodeWriter
    val byteMatrix = qrWriter.encode(data, BarcodeFormat.QR_CODE, width, height, hintMap)
    val writableImage = new WritableImage(width, height)
    val pixelWriter = writableImage.getPixelWriter
    for (i <- 0 to byteMatrix.getWidth - 1) {
      for (j <- 0 to byteMatrix.getWidth - 1) {
        if (byteMatrix.get(i, j)) {
          pixelWriter.setColor(i, j, Color.BLACK)
        } else {
          pixelWriter.setColor(i, j, Color.WHITE)
        }
      }
    }
    writableImage
  }

  @FXML def handleClose(event: ActionEvent) = stage.close
}
