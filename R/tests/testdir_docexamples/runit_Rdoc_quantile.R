
setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../findNSourceUtils.R')

test.rdocquantiles.golden <- function(H2Oserver) {

prosPath = system.file("extdata", "prostate.csv", package="h2o")
prostate.hex = h2o.importFile(H2Oserver, path = prosPath)
quantile(prostate.hex[,3])
for(i in 1:ncol(prostate.hex))
  quantile(prostate.hex[,i])

testEnd()
}

doTest("R Doc Quantiles", test.rdocquantiles.golden)

