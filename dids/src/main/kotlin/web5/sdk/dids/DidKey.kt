package web5.sdk.dids

import com.nimbusds.jose.Algorithm
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.Curve
import foundation.identity.did.DID
import foundation.identity.did.DIDDocument
import foundation.identity.did.VerificationMethod
import io.ipfs.multibase.Multibase
import web5.sdk.common.Varint
import web5.sdk.crypto.Crypto
import web5.sdk.crypto.KeyManager
import java.net.URI

// TODO: move this to Crypto
private val CURVE_CODEC_IDS = mapOf(
  Curve.Ed25519 to Varint.encode(0xed),
  Curve.SECP256K1 to Varint.encode(0xe7)
)

/**
 * Represents options for creating "did:key" DIDs with specified cryptographic configurations.
 *
 * This class is designed to hold specific configurations, namely the [algorithm] and [curve],
 * during the creation of a new "did:key" DID using the [DidKeyMethod] implementation.
 *
 * @property algorithm A JOSE [Algorithm] to be used for generating the key (defaults to EdDSA).
 * @property curve A cryptographic [Curve] to be used for key generation (defaults to Ed25519).
 *
 * ### Example Usage:
 * ```
 * val options = CreateDidKeyOptions(algorithm = JWSAlgorithm.EdDSA, curve = Curve.Ed25519)
 * ```
 */
public class CreateDidKeyOptions(
  public val algorithm: Algorithm = JWSAlgorithm.EdDSA,
  public val curve: Curve = Curve.Ed25519,
) : CreateDidOptions

public typealias DidKey = Did

/**
 * Provides a specific implementation for creating and resolving "did:key" method Decentralized Identifiers (DIDs).
 *
 * The "did:key" method is a specific DID method that is intended for use with
 * DIDs that are entirely derived from a single public key, and it's described in detail
 * in the W3C DID specification.
 *
 * ### Usage Example:
 * ```
 * val keyManager = InMemoryKeyManager()
 * val did = DidKeyMethod.create(keyManager)
 * ```
 */
public object DidKeyMethod : DidMethod<CreateDidKeyOptions> {
  override val method: String = "key"

  /**
   * Creates a new "did:key" DID, derived from a public key, and stores the associated key in the provided [KeyManager].
   *
   * The method-specific identifier of a "did:key" DID is a multibase encoded public key.
   *
   * **Note**: Defaults to Ed25519 if no options are provided
   *
   * @param keyManager A [KeyManager] instance where the new key will be stored.
   * @param options Optional parameters ([CreateDidKeyOptions]) to specify algorithm and curve during key creation.
   * @return A [DidKey] instance representing the newly created "did:key" DID.
   *
   * @throws UnsupportedOperationException if the specified curve is not supported.
   */
  public override suspend fun create(keyManager: KeyManager, options: CreateDidKeyOptions?)
    : Pair<DidKey, CreatedDidKeyMetadata> {
    val opts = options ?: CreateDidKeyOptions()

    val keyAlias = keyManager.generatePrivateKey(opts.algorithm, opts.curve)
    val publicKey = keyManager.getPublicKey(keyAlias)
    val publicKeyBytes = Crypto.getPublicKeyBytes(publicKey)

    val codecId = CURVE_CODEC_IDS.getOrElse(opts.curve) {
      throw UnsupportedOperationException("${opts.curve} curve not supported")
    }

    val idBytes = codecId + publicKeyBytes
    val multibaseEncodedId = Multibase.encode(Multibase.Base.Base58BTC, idBytes)

    val did = "did:key:$multibaseEncodedId"

    return Pair(
      DidKey(keyManager, did),
      CreatedDidKeyMetadata(keyAlias)
    )
  }

  /**
   * Resolves a "did:key" DID into a [DidResolutionResult], which contains the DID Document and possible related metadata.
   *
   * This implementation primarily constructs a DID Document with a single verification method derived
   * from the DID's method-specific identifier (the public key).
   *
   * @param didUrl The "did:key" DID that needs to be resolved.
   * @return A [DidResolutionResult] instance containing the DID Document and related context.
   *
   * @throws IllegalArgumentException if the provided DID does not conform to the "did:key" method.
   */
  public override suspend fun resolve(didUrl: String): DidResolutionResult {
    val parsedDid = DID.fromString(didUrl)

    require(parsedDid.methodName == method) { throw IllegalArgumentException("expected did:key") }

    val id = parsedDid.methodSpecificId
    val idBytes = Multibase.decode(id)
    val (multiCodec, numBytes) = Varint.decode(idBytes)

    val publicKeyBytes = idBytes.drop(numBytes).toByteArray()

    val keyGenerator = Crypto.getKeyGenerator(multiCodec)
    val publicKeyJwk = keyGenerator.bytesToPublicKey(publicKeyBytes)

    val verificationMethodId = URI.create("$didUrl#$id")
    val verificationMethod = VerificationMethod.builder()
      .id(verificationMethodId)
      .publicKeyJwk(publicKeyJwk.toJSONObject())
      .controller(URI(didUrl))
      .type("JsonWebKey2020")
      .build()

    val didDocument = DIDDocument.builder()
      .id(URI(didUrl))
      .verificationMethod(verificationMethod)
      .build()

    return DidResolutionResult(didDocument = didDocument, context = "https://w3id.org/did-resolution/v1")
  }
}

/**
 * Contains metadata related to the creation of a did key.
 *
 * @param keyAlias The key alias that was created in the [KeyManager].
 */
public class CreatedDidKeyMetadata(
  public val keyAlias: String
) : CreationMetadata